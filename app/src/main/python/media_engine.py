import base64
import html
import json
import os
import re
import sys
import tempfile
import traceback
from urllib.parse import parse_qs, urlencode, urlparse, urlunparse
from urllib.request import Request, urlopen

try:
    from com.chaquo.python import Python
    _context = Python.getPlatform().getApplication()
    _files_dir = _context.getFilesDir().getAbsolutePath()
    _update_dir = os.path.join(_files_dir, "python_updates")
    if os.path.isdir(_update_dir):
        if _update_dir not in sys.path:
            sys.path.insert(0, _update_dir)
except Exception:
    pass

from yt_dlp import YoutubeDL

try:
    import chaquopy_quickjs_provider  # noqa: F401
except Exception:
    chaquopy_quickjs_provider = None

MAX_RESOLVE_ATTEMPTS = 2
MAX_DOWNLOAD_ATTEMPTS = 2


def version():
    import yt_dlp

    return yt_dlp.version.__version__


def formats(
    url,
    cookie_header="",
    referer_header="",
    user_agent="",
    video_format_preference=1,
    audio_format_preference=0,
    audio_quality_preference=0,
):
    urls = _url_candidates(url)

    result = {
        "ok": False,
        "title": "",
        "thumbnail": "",
        "formats": [],
        "error": "",
        "engine_version": version(),
    }

    base_options = {
        "noplaylist": True,
        "playlist_items": "1",
        "no_warnings": True,
        "quiet": True,
        "skip_download": True,
    }

    errors = []
    attempts_used = 0
    try:
        for candidate_url in urls:
            if attempts_used >= MAX_RESOLVE_ATTEMPTS:
                break
            if _is_threads_url(candidate_url):
                attempts_used += 1
                threads_result = _threads_format_result(
                    candidate_url,
                    cookie_header=cookie_header,
                    referer_header=referer_header,
                    user_agent=user_agent,
                )
                if threads_result.get("ok"):
                    result.update(threads_result)
                    return json.dumps(result, ensure_ascii=False)
                errors.append(threads_result.get("error") or "Threads 规则解析失败")
                continue
            for attempt in _format_attempts(candidate_url, cookie_header):
                if attempts_used >= MAX_RESOLVE_ATTEMPTS:
                    break
                attempts_used += 1
                extractor_args = attempt.get("extractor_args")
                attempt_cookie = attempt.get("cookie_header")
                if attempt_cookie is None:
                    attempt_cookie = cookie_header if attempt.get("use_cookie", True) else ""
                cookiefile = _write_cookie_file(candidate_url, attempt_cookie)
                options = dict(base_options)
                if _is_twitter_url(candidate_url):
                    options.pop("playlist_items", None)
                try:
                    options["http_headers"] = _http_headers(candidate_url, cookie_header=attempt_cookie, referer_header=referer_header, user_agent=user_agent)
                    if cookiefile:
                        options["cookiefile"] = cookiefile
                    if extractor_args:
                        options["extractor_args"] = extractor_args
                    with YoutubeDL(options) as ydl:
                        info = ydl.extract_info(candidate_url, download=False)
                except Exception as exc:
                    errors.append(_attempt_error(candidate_url, attempt, str(exc) or traceback.format_exc(limit=1)))
                    continue
                finally:
                    _delete_file(cookiefile)

                if info is None:
                    errors.append("解析失败：没有获取到媒体信息")
                    continue

                media_info, _ = _preferred_downloadable_info(info, candidate_url)
                platform_metadata = _platform_page_metadata(
                    candidate_url,
                    attempt_cookie,
                    referer_header,
                    user_agent,
                )
                formats = _format_options(
                    media_info,
                    candidate_url,
                    video_format_preference=video_format_preference,
                    audio_format_preference=audio_format_preference,
                    audio_quality_preference=audio_quality_preference,
                )
                resolved_title = media_info.get("title") or info.get("title") or "未命名媒体"
                resolved_thumbnail = _thumbnail_url(media_info, info)
                result["ok"] = True
                result["title"] = _prefer_platform_title(candidate_url, platform_metadata.get("title"), resolved_title)
                result["thumbnail"] = _prefer_platform_thumbnail(candidate_url, platform_metadata.get("thumbnail"), resolved_thumbnail)
                result["formats"] = formats
                return json.dumps(result, ensure_ascii=False)

        result["error"] = _compose_error("解析失败：没有找到可下载视频", errors)
    except Exception as exc:
        result["error"] = str(exc) or traceback.format_exc(limit=1)

    return json.dumps(result, ensure_ascii=False)


def download(url, output_dir, cancel_path=None, progress_path=None, format_selector=None, ffmpeg_path=None, cookie_header="", referer_header="", user_agent="", rate_limit_bytes=0):
    urls = _url_candidates(url)

    os.makedirs(output_dir, exist_ok=True)
    _remove_stale_partial_files(output_dir)
    existing_media_files = _media_file_snapshot(output_dir)

    progress = []
    log_messages = []
    result = {
        "ok": False,
        "title": "",
        "filepath": "",
        "error": "",
        "engine_version": version(),
    }

    progress_state = {"items": {}, "progress": 0.0}

    def hook(event):
        if cancel_path and os.path.exists(cancel_path):
            raise RuntimeError("下载已取消")

        status = event.get("status")
        if status in ("downloading", "finished"):
            snapshot = _aggregate_progress_snapshot(event, progress_state)
            progress.append(snapshot)
            _write_progress(progress_path, snapshot)

    base_options = {
        "merge_output_format": "mp4",
        "noplaylist": True,
        "playlist_items": "1",
        "no_warnings": True,
        "outtmpl": os.path.join(output_dir, "%(title).180B [%(id)s].%(ext)s"),
        "progress_hooks": [hook],
        "quiet": True,
        "restrictfilenames": True,
        "ignoreerrors": False,
        "continuedl": False,
        "nocheckcertificate": False,
        "socket_timeout": 30,
        "retries": 3,
        "fragment_retries": 3,
        "skip_unavailable_fragments": False,
        "logger": _YdlLogger(log_messages),
    }
    if ffmpeg_path:
        base_options["ffmpeg_location"] = ffmpeg_path
    if rate_limit_bytes and int(rate_limit_bytes) > 0:
        base_options["ratelimit"] = int(rate_limit_bytes)

    errors = []
    attempts_used = 0
    try:
        for candidate_url in urls:
            if attempts_used >= MAX_DOWNLOAD_ATTEMPTS:
                break
            if _is_direct_media_url(candidate_url):
                attempts_used += 1
                direct = _download_direct_media(
                    candidate_url,
                    output_dir,
                    progress_path,
                    cancel_path,
                    cookie_header,
                    referer_header,
                    user_agent,
                )
                if direct.get("ok"):
                    result["ok"] = True
                    result["title"] = direct.get("title") or "媒体文件"
                    result["filepath"] = direct.get("filepath") or ""
                    return json.dumps(result, ensure_ascii=False)
                errors.append(direct.get("error") or "直链下载失败")
                continue

            download_attempts = _download_attempts(candidate_url, cookie_header)
            if format_selector:
                download_attempts = _unique_attempts_for_selected_format(download_attempts)
            for attempt in download_attempts:
                if attempts_used >= MAX_DOWNLOAD_ATTEMPTS:
                    break
                extractor_args = attempt.get("extractor_args")
                attempt_cookie = attempt.get("cookie_header")
                if attempt_cookie is None:
                    attempt_cookie = cookie_header if attempt.get("use_cookie", True) else ""
                cookiefile = _write_cookie_file(candidate_url, attempt_cookie)
                try:
                    twitter_playlist_item = None
                    if _is_twitter_url(candidate_url):
                        probe_options = dict(base_options)
                        probe_options["skip_download"] = True
                        probe_options.pop("playlist_items", None)
                        probe_options["http_headers"] = _http_headers(candidate_url, cookie_header=attempt_cookie, referer_header=referer_header, user_agent=user_agent)
                        if cookiefile:
                            probe_options["cookiefile"] = cookiefile
                        if extractor_args:
                            probe_options["extractor_args"] = extractor_args
                        try:
                            with YoutubeDL(probe_options) as ydl:
                                probe_info = ydl.extract_info(candidate_url, download=False)
                            _, twitter_playlist_item = _preferred_downloadable_info(probe_info, candidate_url)
                        except Exception:
                            twitter_playlist_item = None

                    for selector in _download_format_selectors(format_selector, attempt, candidate_url):
                        if attempts_used >= MAX_DOWNLOAD_ATTEMPTS:
                            break
                        attempts_used += 1
                        options = dict(base_options)
                        if twitter_playlist_item is not None:
                            options["playlist_items"] = str(twitter_playlist_item)
                        if selector:
                            options["format"] = selector
                        error_attempt = dict(attempt)
                        error_attempt["format"] = selector or "default"
                        try:
                            options["http_headers"] = _http_headers(candidate_url, cookie_header=attempt_cookie, referer_header=referer_header, user_agent=user_agent)
                            if cookiefile:
                                options["cookiefile"] = cookiefile
                            if extractor_args:
                                options["extractor_args"] = extractor_args
                            with YoutubeDL(options) as ydl:
                                info = ydl.extract_info(candidate_url, download=True)
                        except Exception as exc:
                            errors.append(_attempt_error(candidate_url, error_attempt, _compose_error(str(exc) or traceback.format_exc(limit=1), log_messages)))
                            continue

                        if info is None:
                            errors.append(_attempt_error(candidate_url, error_attempt, "下载失败：没有获取到媒体信息"))
                            continue

                        filepath = _resolve_filepath(info, progress, output_dir, existing_media_files)
                        if not _is_readable_file(filepath):
                            errors.append(_attempt_error(candidate_url, error_attempt, "下载失败：没有生成可用文件"))
                            continue

                        result["ok"] = True
                        platform_metadata = _platform_page_metadata(
                            candidate_url,
                            attempt_cookie,
                            referer_header,
                            user_agent,
                        )
                        result["title"] = _prefer_platform_title(
                            candidate_url,
                            platform_metadata.get("title"),
                            info.get("title") or "未命名媒体",
                        )
                        result["filepath"] = filepath
                        return json.dumps(result, ensure_ascii=False)
                finally:
                    _delete_file(cookiefile)

        result["error"] = _compose_error("下载失败：没有找到可下载视频", errors)
    except Exception as exc:
        result["ok"] = False
        if str(exc) == "下载已取消":
            result["error"] = "下载已取消"
        else:
            result["error"] = _compose_error(str(exc) or traceback.format_exc(limit=1), log_messages)

    try:
        if progress_path:
            log_file_path = os.path.join(os.path.dirname(progress_path), "py_error_log.txt")
            with open(log_file_path, "w", encoding="utf-8") as f:
                f.write("\n".join(log_messages))
    except Exception:
        pass

    return json.dumps(result, ensure_ascii=False)


def _download_direct_media(url, output_dir, progress_path, cancel_path, cookie_header="", referer_header="", user_agent=""):
    result = {"ok": False, "title": "", "filepath": "", "error": ""}
    filepath = os.path.join(output_dir, _direct_media_filename(url))
    temp_path = filepath + ".part"
    headers = _http_headers(url, cookie_header=cookie_header, referer_header=referer_header, user_agent=user_agent)
    headers["Accept"] = "*/*"
    headers["Connection"] = "keep-alive"
    if cookie_header and not cookie_header.lstrip().startswith("# Netscape HTTP Cookie File"):
        headers["Cookie"] = " ".join(cookie_header.replace("\n", " ").split())
    request = Request(url, headers=headers)

    downloaded = 0
    try:
        with urlopen(request, timeout=30) as response:
            status = getattr(response, "status", 200)
            if status >= 400:
                result["error"] = f"直链下载失败：HTTP {status}"
                return result
            total = int(response.headers.get("Content-Length") or 0)
            content_type = response.headers.get("Content-Type") or ""
            if content_type and not _content_type_is_media(content_type):
                result["error"] = f"直链下载失败：响应不是媒体文件 ({content_type})"
                return result

            with open(temp_path, "wb") as file:
                while True:
                    if cancel_path and os.path.exists(cancel_path):
                        raise RuntimeError("下载已取消")
                    chunk = response.read(256 * 1024)
                    if not chunk:
                        break
                    file.write(chunk)
                    downloaded += len(chunk)
                    _write_progress(
                        progress_path,
                        {
                            "status": "downloading",
                            "filename": filepath,
                            "downloaded_bytes": downloaded,
                            "total_bytes": total,
                            "speed": 0,
                            "eta": 0,
                        },
                    )
        if downloaded <= 0:
            result["error"] = "直链下载失败：没有接收到媒体数据"
            return result
        os.replace(temp_path, filepath)
        _write_progress(
            progress_path,
            {
                "status": "finished",
                "filename": filepath,
                "downloaded_bytes": downloaded,
                "total_bytes": downloaded,
                "speed": 0,
                "eta": 0,
            },
        )
        result["ok"] = True
        result["title"] = os.path.splitext(os.path.basename(filepath))[0]
        result["filepath"] = filepath
        return result
    except Exception as exc:
        if str(exc) == "下载已取消":
            result["error"] = "下载已取消"
        else:
            result["error"] = str(exc) or traceback.format_exc(limit=1)
        return result
    finally:
        if os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except OSError:
                pass


def _aggregate_progress_snapshot(event, state):
    filename = _canonical_progress_filename(event.get("filename") or event.get("tmpfilename") or "media")
    status = event.get("status") or "downloading"
    downloaded = int(event.get("downloaded_bytes") or 0)
    total = int(event.get("total_bytes") or event.get("total_bytes_estimate") or 0)
    previous = state["items"].get(filename) or {}

    if total > 0 and downloaded > total:
        total = downloaded
    if status == "finished" and total <= 0:
        total = int(previous.get("total") or downloaded)

    state["items"][filename] = {
        "downloaded": downloaded,
        "total": total,
        "finished": status == "finished",
    }

    aggregate_downloaded = 0
    aggregate_total = 0
    for item in state["items"].values():
        item_downloaded = int(item.get("downloaded") or 0)
        item_total = int(item.get("total") or 0)
        if item_total > 0:
            aggregate_downloaded += min(item_downloaded, item_total)
            aggregate_total += item_total
        else:
            aggregate_downloaded += item_downloaded

    progress_value = 0.0
    if aggregate_total > 0:
        progress_value = max(0.0, min(1.0, aggregate_downloaded / aggregate_total))
    state["progress"] = progress_value

    return {
        "status": status,
        "filename": filename,
        "downloaded_bytes": aggregate_downloaded,
        "total_bytes": aggregate_total,
        "progress": state["progress"],
        "speed": event.get("speed") or 0,
        "eta": event.get("eta") or 0,
    }


def _canonical_progress_filename(filename):
    marker = ".part-Frag"
    if marker in filename and filename.endswith(".part"):
        return filename.split(marker, 1)[0] + ".part"
    return filename


def _resolve_filepath(info, progress, output_dir="", existing_media_files=None):
    if not info:
        return ""

    candidates = []

    _collect_info_filepaths(info, candidates)

    for item in reversed(progress):
        _append_candidate(candidates, item.get("filename", ""))

    _append_candidate(candidates, info.get("filepath") or info.get("_filename"))

    merged_video = [path for path in candidates if _is_video_file(path) and ".f" not in os.path.basename(path)]
    if merged_video:
        return merged_video[0]

    video_stream = [path for path in candidates if _is_video_file(path)]
    if video_stream:
        return video_stream[0]

    audio_stream = [path for path in candidates if _is_audio_file(path)]
    if audio_stream:
        return audio_stream[0]

    return _new_downloaded_media_file(output_dir, existing_media_files or {})


def _collect_info_filepaths(info, candidates):
    if not isinstance(info, dict):
        return

    requested = info.get("requested_downloads") or []
    for item in requested:
        _append_candidate(candidates, item.get("filepath") or item.get("_filename"))

    _append_candidate(candidates, info.get("filepath") or info.get("_filename"))

    entries = info.get("entries") or []
    for entry in entries:
        _collect_info_filepaths(entry, candidates)


def _media_file_snapshot(output_dir):
    snapshot = {}
    if not output_dir or not os.path.isdir(output_dir):
        return snapshot
    for filename in os.listdir(output_dir):
        filepath = os.path.join(output_dir, filename)
        if not (_is_video_file(filepath) or _is_audio_file(filepath)):
            continue
        if not _is_readable_file(filepath):
            continue
        try:
            snapshot[filepath] = (os.path.getmtime(filepath), os.path.getsize(filepath))
        except OSError:
            continue
    return snapshot


def _new_downloaded_media_file(output_dir, existing_media_files):
    current = _media_file_snapshot(output_dir)
    candidates = []
    for filepath, state in current.items():
        if filepath not in existing_media_files or existing_media_files.get(filepath) != state:
            candidates.append((filepath, state))
    if not candidates:
        return ""
    candidates.sort(key=lambda item: (item[1][0], item[1][1]), reverse=True)
    video = [item[0] for item in candidates if _is_video_file(item[0])]
    return (video or [item[0] for item in candidates])[0]


def _threads_format_result(url, cookie_header="", referer_header="", user_agent=""):
    result = {"ok": False, "title": "", "thumbnail": "", "formats": [], "error": ""}
    try:
        headers = _http_headers(
            url,
            cookie_header=cookie_header,
            referer_header=referer_header or "https://www.threads.com/",
            user_agent=user_agent or _desktop_user_agent(),
        )
        headers["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        if cookie_header and not cookie_header.lstrip().startswith("# Netscape HTTP Cookie File"):
            headers["Cookie"] = " ".join(cookie_header.replace("\n", " ").split())
        request = Request(url, headers=headers)
        with urlopen(request, timeout=25) as response:
            raw = response.read(3 * 1024 * 1024)
            page = raw.decode(response.headers.get_content_charset() or "utf-8", errors="ignore")
    except Exception as exc:
        result["error"] = f"Threads 页面请求失败：{exc}"
        return result

    title = _threads_title_from_page(page)
    result["thumbnail"] = _threads_thumbnail_from_page(page)
    media_urls = _threads_media_urls_from_page(page)
    if not media_urls:
        result["title"] = title
        result["error"] = "Threads 页面中未找到 Instagram CDN 视频地址"
        return result

    options = []
    for index, media_url in enumerate(media_urls, start=1):
        metadata = _threads_media_metadata(media_url)
        height = int(metadata.get("height") or 0)
        duration = int(metadata.get("duration") or 0)
        label = f"{height}P" if height > 0 else f"视频 {index}"
        detail_parts = ["Instagram CDN"]
        if duration > 0:
            detail_parts.append(_format_duration(duration))
        asset_id = metadata.get("asset_id") or ""
        if asset_id:
            detail_parts.append(asset_id[-6:])
        options.append(
            {
                "id": f"threads_{index}_{height or 0}",
                "label": label,
                "detail": " · ".join(detail_parts),
                "selector": "",
                "height": height,
                "requires_merge": False,
                "url": media_url,
            }
        )

    options.sort(key=lambda item: (item.get("height") or 0, item.get("detail") or ""), reverse=True)
    result["ok"] = True
    result["title"] = title or "Threads 视频"
    result["formats"] = options
    return result


def _threads_title_from_page(page):
    normalized = _decode_threads_escapes(page)
    for pattern in (
        r'<meta[^>]+property=["\']og:title["\'][^>]+content=["\']([^"\']+)["\']',
        r'<title[^>]*>(.*?)</title>',
    ):
        match = re.search(pattern, normalized, flags=re.IGNORECASE | re.DOTALL)
        if match:
            title = html.unescape(match.group(1))
            title = re.sub(r"\s+", " ", title).strip()
            title = re.sub(r"\s*\|\s*Threads\s*$", "", title).strip()
            if title:
                return title[:180]
    return ""


def _threads_thumbnail_from_page(page):
    normalized = _decode_threads_escapes(page)
    for pattern in (
        r'<meta[^>]+property=["\']og:image["\'][^>]+content=["\']([^"\']+)["\']',
        r'<meta[^>]+name=["\']twitter:image["\'][^>]+content=["\']([^"\']+)["\']',
    ):
        match = re.search(pattern, normalized, flags=re.IGNORECASE | re.DOTALL)
        if match:
            value = html.unescape(match.group(1)).strip()
            if value.startswith("http://") or value.startswith("https://"):
                return _prefer_https_thumbnail(value)
    return ""


def _platform_page_metadata(url, cookie_header="", referer_header="", user_agent=""):
    if _is_xiaohongshu_url(url):
        return _xiaohongshu_page_metadata(url, cookie_header, referer_header, user_agent)
    return {}


def _prefer_platform_title(url, platform_title, fallback_title):
    fallback = (fallback_title or "").strip()
    title = (platform_title or "").strip()
    if not _is_xiaohongshu_url(url):
        return fallback
    if title and (not fallback or _is_generic_xiaohongshu_title(fallback)):
        return title
    return fallback or title


def _prefer_platform_thumbnail(url, platform_thumbnail, fallback_thumbnail):
    thumbnail = (platform_thumbnail or "").strip()
    fallback = (fallback_thumbnail or "").strip()
    if _is_xiaohongshu_url(url) and thumbnail:
        return thumbnail
    return fallback or thumbnail


def _xiaohongshu_page_metadata(url, cookie_header="", referer_header="", user_agent=""):
    result = {"title": "", "thumbnail": ""}
    try:
        headers = _http_headers(
            url,
            cookie_header=cookie_header,
            referer_header=referer_header,
            user_agent=user_agent,
        )
        headers["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        if cookie_header and not cookie_header.lstrip().startswith("# Netscape HTTP Cookie File"):
            headers["Cookie"] = " ".join(cookie_header.replace("\n", " ").split())
        request = Request(url, headers=headers)
        with urlopen(request, timeout=12) as response:
            raw = response.read(4 * 1024 * 1024)
            page = raw.decode(response.headers.get_content_charset() or "utf-8", errors="ignore")
    except Exception:
        return result

    normalized = _decode_html_json_escapes(page)
    result["title"] = _xiaohongshu_title_from_page(normalized)
    result["thumbnail"] = _xiaohongshu_thumbnail_from_page(normalized)
    return result


def _xiaohongshu_title_from_page(page):
    candidates = []
    for pattern in (
        r'<meta[^>]+property=["\']og:title["\'][^>]+content=["\']([^"\']+)["\']',
        r'<meta[^>]+name=["\']twitter:title["\'][^>]+content=["\']([^"\']+)["\']',
        r'<meta[^>]+name=["\']description["\'][^>]+content=["\']([^"\']+)["\']',
        r'<title[^>]*>(.*?)</title>',
        r'"displayTitle"\s*:\s*"((?:\\.|[^"\\]){1,500})"',
        r'"title"\s*:\s*"((?:\\.|[^"\\]){1,500})"',
        r'"desc"\s*:\s*"((?:\\.|[^"\\]){1,500})"',
    ):
        for match in re.finditer(pattern, page, flags=re.IGNORECASE | re.DOTALL):
            value = _clean_xiaohongshu_title(_decode_jsonish_string(match.group(1)))
            if value and value not in candidates:
                candidates.append(value)

    candidates.sort(key=_xiaohongshu_title_score, reverse=True)
    return candidates[0] if candidates else ""


def _xiaohongshu_thumbnail_from_page(page):
    candidates = []
    for pattern in (
        r'<meta[^>]+property=["\']og:image["\'][^>]+content=["\']([^"\']+)["\']',
        r'<meta[^>]+name=["\']twitter:image["\'][^>]+content=["\']([^"\']+)["\']',
        r'"(?:urlDefault|urlPre|url|image|cover)"\s*:\s*"((?:https?:)?//(?:[^"\\]|\\.)+)"',
        r'(https?://[^"\'<>\s]+(?:xhscdn|sns-webpic|sns-img)[^"\'<>\s]+)',
    ):
        for match in re.finditer(pattern, page, flags=re.IGNORECASE):
            value = _decode_jsonish_string(match.group(1)).strip()
            if value.startswith("//"):
                value = "https:" + value
            if not value.startswith(("http://", "https://")):
                continue
            value = _prefer_https_thumbnail(value)
            if value not in candidates and _xiaohongshu_image_score(value) > -100:
                candidates.append(value)
    candidates.sort(key=_xiaohongshu_image_score, reverse=True)
    return candidates[0] if candidates else ""


def _decode_html_json_escapes(text):
    value = html.unescape(text or "")
    value = value.replace("\\/", "/")
    value = re.sub(
        r"\\u([0-9a-fA-F]{4})",
        lambda match: chr(int(match.group(1), 16)),
        value,
    )
    return value


def _decode_jsonish_string(value):
    raw = html.unescape(value or "")
    try:
        return json.loads(f'"{raw}"')
    except Exception:
        return _decode_html_json_escapes(raw)


def _clean_xiaohongshu_title(title):
    value = re.sub(r"\s+", " ", title or "").strip()
    value = re.sub(r"\s*[-_|]\s*(小红书|REDnote)\s*$", "", value, flags=re.IGNORECASE).strip()
    value = re.sub(r"^小红书\s*[-_|]\s*", "", value).strip()
    if not value:
        return ""
    if value.startswith(("http://", "https://")):
        return ""
    if _is_generic_xiaohongshu_title(value):
        return ""
    return value[:180]


def _is_generic_xiaohongshu_title(title):
    value = (title or "").strip().lower()
    if not value:
        return True
    generic = {
        "小红书",
        "rednote",
        "xiaohongshu",
        "xiaohongshu video",
        "未命名媒体",
        "媒体文件",
    }
    if value in generic:
        return True
    return bool(re.match(r"^xiaohongshu video\s+#?[0-9a-f]{8,}", value))


def _xiaohongshu_title_score(title):
    value = title or ""
    score = min(len(value), 120)
    if re.search(r"[\u4e00-\u9fff]", value):
        score += 40
    if "#" in value:
        score -= 8
    return score


def _xiaohongshu_image_score(url):
    value = (url or "").lower()
    score = 0
    if "sns-webpic" in value or "sns-img" in value or "xhscdn" in value:
        score += 80
    if "avatar" in value or "icon" in value or "profile" in value:
        score -= 180
    if "imageview2" in value:
        score += 20
    widths = [int(item) for item in re.findall(r"(?:/w/|[?&]w=|imageview2/\d+/w/)(\d{2,4})", value)]
    heights = [int(item) for item in re.findall(r"(?:/h/|[?&]h=|imageview2/\d+/h/)(\d{2,4})", value)]
    if widths:
        score += max(widths)
    if heights:
        score += max(heights)
    if not widths and not heights:
        score += min(len(value), 240)
    return score


def _threads_media_urls_from_page(page):
    normalized = _decode_threads_escapes(page)
    candidates = []
    for match in re.finditer(r"https?://[^\s\"'<>]+", normalized):
        candidate = html.unescape(match.group(0)).rstrip("\\,.;)")
        if _is_meta_media_url(candidate) and candidate not in candidates:
            candidates.append(candidate)
    candidates.sort(key=_threads_media_url_score, reverse=True)
    return candidates[:12]


def _decode_threads_escapes(text):
    value = html.unescape(text or "")
    value = value.replace("\\/", "/")
    value = re.sub(
        r"\\u00([0-9a-fA-F]{2})",
        lambda match: chr(int(match.group(1), 16)),
        value,
    )
    return value


def _threads_media_metadata(media_url):
    metadata = {}
    query = parse_qs(urlparse(media_url).query)
    efg = (query.get("efg") or [""])[0]
    if efg:
        try:
            payload = efg + "=" * (-len(efg) % 4)
            decoded = base64.urlsafe_b64decode(payload.encode("utf-8")).decode("utf-8", errors="ignore")
            data = json.loads(decoded)
            tag = str(data.get("vencode_tag") or data.get("xpv_vencode_tag") or "")
            metadata["duration"] = int(float(data.get("duration_s") or 0))
            metadata["asset_id"] = str(data.get("xpv_asset_id") or data.get("asset_id") or "")
            height = _height_from_text(tag)
            if height:
                metadata["height"] = height
        except Exception:
            pass
    if not metadata.get("height"):
        metadata["height"] = _height_from_text(media_url)
    return metadata


def _threads_media_url_score(media_url):
    metadata = _threads_media_metadata(media_url)
    score = int(metadata.get("height") or 0)
    if ".mp4" in media_url.lower():
        score += 100
    if "cdninstagram.com" in media_url.lower():
        score += 50
    return score


def _height_from_text(text):
    value = str(text or "")
    matches = [int(item) for item in re.findall(r"(?<!\d)([1-9]\d{2,3})(?!\d)", value)]
    plausible = [item for item in matches if 240 <= item <= 4320]
    return max(plausible) if plausible else 0


def _format_duration(seconds):
    seconds = max(0, int(seconds))
    minutes, remain = divmod(seconds, 60)
    hours, minutes = divmod(minutes, 60)
    if hours:
        return f"{hours}:{minutes:02d}:{remain:02d}"
    return f"{minutes}:{remain:02d}"


def _format_options(
    info,
    url="",
    video_format_preference=1,
    audio_format_preference=0,
    audio_quality_preference=0,
):
    formats = info.get("formats") or []
    by_height = {}
    audio_format = _best_audio_format(
        formats,
        audio_format_preference=audio_format_preference,
        audio_quality_preference=audio_quality_preference,
    )

    for item in formats:
        height = item.get("height") or 0
        if height <= 0 or not _has_video_track(item):
            continue
        if not _has_audio_plan(item, audio_format):
            continue
        current = by_height.get(height)
        if current is None or _format_score(
            item,
            video_format_preference=video_format_preference,
        ) > _format_score(
            current,
            video_format_preference=video_format_preference,
        ):
            by_height[height] = item

    if not by_height:
        audio_detail_parts = _audio_detail_parts(audio_format)
        return [
            {
                "id": "best",
                "label": "最佳有声质量",
                "detail": " · ".join(["未识别到可锁定的含音轨清晰度"] + audio_detail_parts),
                "selector": _audio_required_selector_for_url(url),
                "height": 0,
                "audio_bitrate_kbps": _audio_bitrate_kbps(audio_format),
                "audio_channels": _audio_channels_label(audio_format),
            }
        ]

    options = []
    for height in sorted(by_height.keys(), reverse=True):
        item = by_height[height]
        fps = item.get("fps") or 0
        ext = item.get("ext") or "mp4"
        label = f"{height}p"
        detail_parts = [ext.upper()]
        if fps and fps > 30:
            detail_parts.append(f"{int(fps)}fps")
        filesize = item.get("filesize") or item.get("filesize_approx") or 0
        if filesize:
            detail_parts.append(_human_size(filesize))
        audio_info_format = audio_format if _requires_audio_merge(item, audio_format) else item
        audio_detail_parts = _audio_detail_parts(
            audio_info_format,
            show_unknown=_is_twitter_url(url),
        )
        options.append(
            {
                "id": f"height_{height}",
                "label": label,
                "detail": " · ".join(detail_parts + audio_detail_parts),
                "selector": _exact_format_selector(item, audio_format, url),
                "height": height,
                "requires_merge": _requires_audio_merge(item, audio_format),
                "audio_bitrate_kbps": _audio_bitrate_kbps(audio_info_format),
                "audio_channels": _audio_channels_label(audio_info_format),
            }
        )

    return options


def _thumbnail_url(media_info, root_info=None):
    for info in (media_info, root_info or {}):
        value = info.get("thumbnail") or ""
        if isinstance(value, str) and value.startswith(("http://", "https://")):
            return _prefer_https_thumbnail(value)

        thumbnails = info.get("thumbnails") or []
        candidates = []
        for item in thumbnails:
            if not isinstance(item, dict):
                continue
            item_url = item.get("url") or ""
            if not isinstance(item_url, str) or not item_url.startswith(("http://", "https://")):
                continue
            width = int(item.get("width") or 0)
            height = int(item.get("height") or 0)
            preference = int(item.get("preference") or 0)
            candidates.append((preference, width * height, item_url))
        if candidates:
            candidates.sort(reverse=True)
            return _prefer_https_thumbnail(candidates[0][2])
    return ""


def _prefer_https_thumbnail(url):
    if url.startswith("http://"):
        return "https://" + url[len("http://"):]
    return url


def _preferred_downloadable_info(info, url=""):
    if not _is_twitter_url(url):
        return info, None

    entries = info.get("entries") or []
    for index, entry in enumerate(entries, start=1):
        if entry and _has_selectable_video_format(entry):
            return entry, index
    return info, None


def _has_selectable_video_format(info):
    formats = info.get("formats") or []
    audio_format = _best_audio_format(formats)
    return any(
        (item.get("height") or 0) > 0
        and _has_video_track(item)
        and _has_audio_plan(item, audio_format)
        for item in formats
    )


def _format_selector_for_url(url):
    if _is_twitter_url(url):
        return _twitter_format_selector()
    if _is_youtube_url(url):
        return _youtube_format_selector()
    if _is_bilibili_url(url):
        return _bilibili_format_selector()
    return _default_format_selector()


def _audio_required_selector_for_url(url):
    if _is_twitter_url(url):
        return _twitter_format_selector()
    return (
        "best[height<=720][vcodec!=none][acodec!=none]/"
        "best[vcodec!=none][acodec!=none]/"
        "bestvideo[height<=720]+bestaudio/"
        "bestvideo+bestaudio"
    )


def _default_format_selector():
    return (
        "bestvideo[height<=720]+bestaudio/"
        "best[height<=720][vcodec!=none][acodec!=none]/"
        "bestvideo+bestaudio/"
        "best[vcodec!=none][acodec!=none]/best"
    )


def _youtube_format_selector():
    return (
        "best[height<=720][vcodec!=none][acodec!=none][ext=mp4]/"
        "best[height<=720][vcodec!=none][acodec!=none]/"
        "best[vcodec!=none][acodec!=none][ext=mp4]/"
        "best[vcodec!=none][acodec!=none]/"
        "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/"
        "bestvideo[height<=720]+bestaudio/"
        "bestvideo+bestaudio/best"
    )


def _bilibili_format_selector():
    return (
        "bestvideo[height<=720]+bestaudio/"
        "bestvideo+bestaudio/"
        "best[height<=720]/"
        "best"
    )


def _twitter_format_selector():
    return (
        "best[height<=720][vcodec!=none][acodec!=none][ext=mp4]/"
        "best[height<=720][vcodec!=none][acodec!=none]/"
        "best[vcodec!=none][acodec!=none][ext=mp4]/"
        "best[vcodec!=none][acodec!=none]"
    )


def _height_format_selector(height, youtube=False):
    if youtube:
        return (
            f"best[height<={height}][vcodec!=none][acodec!=none][ext=mp4]/"
            f"best[height<={height}][vcodec!=none][acodec!=none]/"
            f"bestvideo[height<={height}][ext=mp4]+bestaudio[ext=m4a]/"
            f"bestvideo[height<={height}]+bestaudio/"
            "best[vcodec!=none][acodec!=none]/bestvideo+bestaudio/best"
        )
    return (
        f"bestvideo[height<={height}]+bestaudio/"
        f"best[height<={height}][vcodec!=none][acodec!=none]/"
        "bestvideo+bestaudio/"
        "best[vcodec!=none][acodec!=none]/best"
    )


def _twitter_height_selector(height):
    return (
        f"best[height<={height}][vcodec!=none][acodec!=none][ext=mp4]/"
        f"best[height<={height}][vcodec!=none][acodec!=none]/"
        + _twitter_format_selector()
    )


def _format_score(item, video_format_preference=1):
    filesize = item.get("filesize") or item.get("filesize_approx") or 0
    tbr = item.get("tbr") or 0
    fps = item.get("fps") or 0
    direct_media = 1 if _is_direct_media_format(item) else 0
    ext = (item.get("ext") or "").lower()
    vcodec = (item.get("vcodec") or "").lower()
    acodec = (item.get("acodec") or "").lower()
    if int(video_format_preference or 0) == 1:
        ext_score = 1 if ext in {"mp4", "m4v"} else 0
        codec_score = 1 if (
            vcodec.startswith("avc")
            or vcodec.startswith("h264")
            or acodec.startswith("mp4a")
            or acodec.startswith("aac")
        ) else 0
        return (direct_media, ext_score, codec_score, fps, tbr, -filesize)
    quality_codec_score = 1 if (
        vcodec.startswith("av01")
        or vcodec.startswith("av1")
        or vcodec.startswith("vp9")
        or vcodec.startswith("vp09")
    ) else 0
    return (direct_media, quality_codec_score, fps, tbr, -filesize)


def _best_audio_format(formats, audio_format_preference=0, audio_quality_preference=0):
    audio_formats = [
        item for item in formats
        if _is_audio_only_format(item)
        and item.get("format_id")
    ]
    if not audio_formats:
        return None
    preferred_formats = _filter_audio_format_preference(audio_formats, audio_format_preference)
    preferred_quality = _filter_audio_quality_preference(preferred_formats, audio_quality_preference)
    return max(
        preferred_quality,
        key=lambda item: (
            item.get("abr") or item.get("tbr") or 0,
            item.get("filesize") or item.get("filesize_approx") or 0,
        ),
    )


def _filter_audio_format_preference(audio_formats, audio_format_preference=0):
    preference = int(audio_format_preference or 0)
    if preference == 1:
        preferred = [
            item for item in audio_formats
            if _audio_format_text(item).find("m4a") >= 0
            or _audio_format_text(item).find("mp4a") >= 0
            or _audio_format_text(item).find("aac") >= 0
        ]
        return preferred or audio_formats
    if preference == 2:
        preferred = [
            item for item in audio_formats
            if _audio_format_text(item).find("opus") >= 0
            or _audio_format_text(item).find("webm") >= 0
        ]
        return preferred or audio_formats
    return audio_formats


def _filter_audio_quality_preference(audio_formats, audio_quality_preference=0):
    target = int(audio_quality_preference or 0)
    if target <= 0:
        return audio_formats
    below_target = [
        item for item in audio_formats
        if 0 < _audio_bitrate_kbps(item) <= target
    ]
    if below_target:
        return below_target
    measured = [
        item for item in audio_formats
        if _audio_bitrate_kbps(item) > 0
    ]
    return measured or audio_formats


def _audio_format_text(item):
    return " ".join(
        str(item.get(key) or "")
        for key in ("ext", "acodec", "format", "format_id", "format_note")
    ).lower()


def _audio_detail_parts(item, show_unknown=False):
    if not item:
        return ["音频码率未知", "声道未知"] if show_unknown else []
    parts = []
    bitrate = _audio_bitrate_kbps(item)
    if bitrate:
        parts.append(f"音频 {bitrate}kbps")
    elif show_unknown:
        parts.append("音频码率未知")
    channels = _audio_channels_label(item)
    if channels:
        parts.append(channels)
    elif show_unknown:
        parts.append("声道未知")
    return parts


def _audio_bitrate_kbps(item):
    if not item:
        return 0
    value = item.get("abr") or 0
    if not value:
        acodec = item.get("acodec") or "none"
        vcodec = item.get("vcodec") or "none"
        if acodec != "none" and vcodec == "none":
            value = item.get("tbr") or 0
    try:
        val_float = float(value)
        # Universal unit correction: if the audio bitrate is abnormally large (e.g. > 10000),
        # it must be in bps instead of kbps.
        if val_float > 10000:
            val_float /= 1000.0
        return int(round(val_float)) if val_float > 0 else 0
    except Exception:
        return 0


def _audio_channels_label(item):
    if not item:
        return ""
    channels = item.get("audio_channels") or item.get("channels") or 0
    try:
        count = int(float(channels))
    except Exception:
        count = 0
    if count == 1:
        return "单声道"
    if count == 2:
        return "双声道"
    if count > 2:
        return f"{count}声道"
    text = f"{item.get('format_note') or ''} {item.get('acodec') or ''}".lower()
    if "mono" in text:
        return "单声道"
    if "stereo" in text:
        return "双声道"
    channel_match = re.search(r"(?<!\d)([1-9](?:\.[0-9])?)\s*ch(?:annels?)?(?![a-z])", text)
    if channel_match:
        value = channel_match.group(1)
        return "双声道" if value == "2" else f"{value}声道"
    return ""


def _requires_audio_merge(video_format, audio_format):
    return (
        bool(video_format.get("format_id"))
        and not _is_direct_media_format(video_format)
        and (video_format.get("acodec") or "none") == "none"
        and audio_format is not None
    )


def _has_audio_plan(video_format, audio_format):
    return (
        _is_direct_media_format(video_format)
        or _requires_audio_merge(video_format, audio_format)
    )


def _has_video_track(item):
    vcodec = item.get("vcodec") or "none"
    return vcodec != "none" or _is_unknown_codec_progressive_media(item)


def _is_audio_only_format(item):
    vcodec = item.get("vcodec") or "none"
    acodec = item.get("acodec") or "none"
    label = f"{item.get('format_id') or ''} {item.get('format_note') or ''}".lower()
    return vcodec == "none" and (acodec != "none" or "audio" in label)


def _is_direct_media_format(item):
    return (item.get("acodec") or "none") != "none" or _is_unknown_codec_progressive_media(item)


def _is_unknown_codec_progressive_media(item):
    protocol = str(item.get("protocol") or "").lower()
    ext = str(item.get("ext") or "").lower()
    return (
        (item.get("height") or 0) > 0
        and not item.get("vcodec")
        and not item.get("acodec")
        and protocol in {"http", "https"}
        and ext in {"mp4", "webm", "mov"}
    )


def _exact_format_selector(video_format, audio_format, url):
    format_id = str(video_format.get("format_id") or "")
    if not format_id:
        height = video_format.get("height") or 0
        return _twitter_height_selector(height) if _is_twitter_url(url) else _height_format_selector(height, youtube=_is_youtube_url(url))
    if _requires_audio_merge(video_format, audio_format):
        return f"{format_id}+{audio_format.get('format_id')}"
    if _is_direct_media_format(video_format):
        return format_id
    if (video_format.get("acodec") or "none") == "none":
        height = video_format.get("height") or 0
        return _twitter_height_selector(height) if _is_twitter_url(url) else _audio_required_selector_for_url(url)
    return format_id


def _download_format_selectors(selected_selector, attempt, url):
    if selected_selector:
        return [selected_selector]
    selectors = []
    _append_unique(selectors, attempt.get("format") or _format_selector_for_url(url))
    return selectors


def _unique_attempts_for_selected_format(attempts):
    unique = []
    seen = set()
    for attempt in attempts:
        key = json.dumps(
            {
                "extractor_args": attempt.get("extractor_args"),
                "cookie_header": attempt.get("cookie_header"),
                "use_cookie": attempt.get("use_cookie", True),
            },
            ensure_ascii=False,
            sort_keys=True,
        )
        if key not in seen:
            seen.add(key)
            unique.append(attempt)
    return unique


def _is_direct_media_url(url):
    parsed = urlparse(url or "")
    path = (parsed.path or "").lower()
    if _is_stream_playlist_path(path):
        return False

    value = (url or "").lower()
    return (
        "googlevideo.com/videoplayback" in value
        or _is_douyin_media_url(url)
        or _is_meta_media_url(url)
        or ".mp4" in path
        or ".m4a" in path
        or ".webm" in path
    )


def _is_douyin_media_url(url):
    parsed = urlparse(url or "")
    host = parsed.netloc.lower()
    path = (parsed.path or "").lower()
    return (
        "douyinvod.com" in host
        or "/video/tos/" in path
        or "/aweme/v1/play" in path
        or "/aweme/v1/playwm" in path
    )


def _is_meta_media_url(url):
    parsed = urlparse(url or "")
    host = parsed.netloc.lower()
    path = (parsed.path or "").lower()
    if not (
        host.endswith(".cdninstagram.com")
        or host.endswith(".fbcdn.net")
        or host.endswith(".fbsbx.com")
    ):
        return False
    return (
        "video" in path
        or ".mp4" in path
        or ".m4v" in path
        or "mime=video" in (parsed.query or "").lower()
    )


def _is_stream_playlist_path(path):
    value = (path or "").lower()
    return value.endswith((".m3u8", ".mpd")) or ".m3u8/" in value or ".mpd/" in value


def _direct_media_filename(url):
    parsed = urlparse(url)
    filename = os.path.basename(parsed.path).strip() or "direct_media"
    video_id = (parse_qs(parsed.query).get("video_id") or [""])[0].strip()
    if filename == "direct_media" and video_id:
        filename = f"direct_media_{video_id[:48]}"
    value = url.lower()
    if "." not in filename:
        if "mime=audio" in value:
            filename += ".m4a"
        elif ".webm" in value or "webm" in value:
            filename += ".webm"
        else:
            filename += ".mp4"
    safe = "".join(char if char.isalnum() or char in "._- " else "_" for char in filename)[:180]
    return safe or "direct_media.mp4"


def _content_type_is_media(content_type):
    value = (content_type or "").lower()
    if "application/vnd.yt-ump" in value:
        return False
    return value.startswith("video/") or value.startswith("audio/") or value in {"application/octet-stream", "binary/octet-stream"}


def _normalize_url(url):
    parsed = urlparse(url)
    host = parsed.netloc.lower()
    if host == "x.com" or host.endswith(".x.com"):
        parsed = parsed._replace(netloc=parsed.netloc.replace("x.com", "twitter.com"))
    return urlunparse(parsed)


def _url_candidates(url):
    candidates = []
    raw = (url or "").strip()
    normalized = _normalize_url(raw)
    if _is_douyin_short_url(raw):
        expanded = _expand_redirect_url(raw)
        for variant in _structural_url_variants(expanded):
            if _is_douyin_canonical_video_url(variant):
                _append_unique(candidates, variant)
        _append_unique(candidates, expanded)
        _append_unique(candidates, _normalize_url(expanded))
    _append_unique(candidates, raw)
    _append_unique(candidates, normalized)

    for candidate in list(candidates):
        if _should_expand_redirect_url(candidate):
            expanded = _expand_redirect_url(candidate)
            _append_unique(candidates, expanded)
            _append_unique(candidates, _normalize_url(expanded))

    for candidate in list(candidates):
        for variant in _structural_url_variants(candidate):
            _append_unique(candidates, variant)

    return candidates or [url]


def _append_unique(items, value):
    if value and value not in items:
        items.append(value)


def _should_expand_redirect_url(url):
    parsed = urlparse(url)
    host = parsed.netloc.lower()
    path_parts = [part for part in parsed.path.split("/") if part]
    if not parsed.scheme.startswith("http"):
        return False
    if host == "b23.tv" or host.endswith(".b23.tv"):
        return True
    if host.startswith("m.") or host.startswith("www.m."):
        return True
    if _is_douyin_short_url(url):
        return True
    if "xhslink.com" in host or "rednote.com" in host:
        return True
    return bool(path_parts and path_parts[0] in {"s", "share", "short", "url", "link", "go", "redirect", "dx"})


def _structural_url_variants(url):
    variants = []
    parsed = urlparse(url)
    host = parsed.netloc.lower()
    parts = [part for part in parsed.path.split("/") if part]

    desktop_host = _desktop_host(host)
    if desktop_host and desktop_host != host:
        _append_unique(variants, urlunparse(parsed._replace(netloc=desktop_host)))

    if len(parts) >= 2 and parts[0] in {"dx", "share", "s", "video"} and parts[1].isdigit():
        canonical_path = f"/{parts[1]}/"
        _append_unique(variants, urlunparse(parsed._replace(path=canonical_path, params="", query="", fragment="")))
        if desktop_host:
            _append_unique(variants, urlunparse(parsed._replace(netloc=desktop_host, path=canonical_path, params="", query="", fragment="")))

    if _is_twitter_url(url) and len(parts) >= 3 and parts[0] == "i" and parts[1] == "status" and parts[2].isdigit():
        tweet_id = parts[2]
        for host_variant in ("x.com", "twitter.com"):
            _append_unique(variants, urlunparse(parsed._replace(netloc=host_variant, path=f"/i/status/{tweet_id}", params="", query="", fragment="")))
            _append_unique(variants, urlunparse(parsed._replace(netloc=host_variant, path=f"/i/web/status/{tweet_id}", params="", query="", fragment="")))

    if _is_weibo_url(url):
        query = parse_qs(parsed.query)
        fid = (query.get("fid") or [""])[0]
        if fid:
            _append_unique(variants, f"https://weibo.com/tv/show/{fid}")
            _append_unique(variants, f"https://video.weibo.com/show?fid={fid}")
        if len(parts) >= 3 and parts[0] == "tv" and parts[1] == "show":
            _append_unique(variants, f"https://video.weibo.com/show?fid={parts[2]}")

    if _is_bilibili_url(url) and len(parts) >= 2 and parts[0] == "video":
        bvid = parts[1]
        canonical_query = ""
        page = (parse_qs(parsed.query).get("p") or [""])[0]
        if page and page != "1":
            canonical_query = urlencode({"p": page})
        _append_unique(
            variants,
            urlunparse(parsed._replace(netloc="www.bilibili.com", path=f"/video/{bvid}", params="", query=canonical_query, fragment="")),
        )

    if _is_douyin_url(url) and len(parts) >= 3 and parts[0] == "share" and parts[1] == "video" and parts[2].isdigit():
        _append_unique(variants, f"https://www.douyin.com/video/{parts[2]}")

    if _is_pornhub_url(url):
        if host.endswith("pornhub.org"):
            _append_unique(variants, urlunparse(parsed._replace(netloc=host.replace("pornhub.org", "pornhub.com"))))
        if host.endswith("pornhub.com"):
            _append_unique(variants, urlunparse(parsed._replace(netloc=host.replace("pornhub.com", "pornhub.org"))))

    return variants


def _desktop_host(host):
    if host.startswith("m."):
        return "www." + host[2:]
    if host.startswith("www.m."):
        return "www." + host[6:]
    return host


def _is_twitter_url(url):
    host = urlparse(url).netloc.lower()
    return host == "twitter.com" or host.endswith(".twitter.com") or host == "x.com" or host.endswith(".x.com")


def _is_threads_url(url):
    host = urlparse(url).netloc.lower()
    return host == "threads.net" or host.endswith(".threads.net") or host == "threads.com" or host.endswith(".threads.com")


def _is_youtube_url(url):
    host = urlparse(url).netloc.lower()
    return host in {"youtube.com", "youtu.be"} or host.endswith(".youtube.com")


def _is_bilibili_url(url):
    host = urlparse(url).netloc.lower()
    return host == "bilibili.com" or host.endswith(".bilibili.com") or host == "b23.tv" or host.endswith(".b23.tv")


def _is_weibo_url(url):
    host = urlparse(url).netloc.lower()
    return host == "weibo.com" or host.endswith(".weibo.com") or host == "weibo.cn" or host.endswith(".weibo.cn")


def _is_douyin_url(url):
    host = urlparse(url).netloc.lower()
    return host == "douyin.com" or host.endswith(".douyin.com") or host == "iesdouyin.com" or host.endswith(".iesdouyin.com")


def _is_douyin_short_url(url):
    host = urlparse(url).netloc.lower()
    return host == "v.douyin.com"


def _is_douyin_canonical_video_url(url):
    parsed = urlparse(url or "")
    parts = [part for part in parsed.path.split("/") if part]
    return (
        parsed.netloc.lower() in {"douyin.com", "www.douyin.com"}
        and len(parts) >= 2
        and parts[0] == "video"
        and parts[1].isdigit()
    )


def _is_ixigua_url(url):
    host = urlparse(url).netloc.lower()
    return host == "ixigua.com" or host.endswith(".ixigua.com")


def _is_pornhub_url(url):
    host = urlparse(url).netloc.lower()
    return host == "pornhub.com" or host.endswith(".pornhub.com") or host == "pornhub.org" or host.endswith(".pornhub.org")


def _is_xiaohongshu_url(url):
    host = urlparse(url).netloc.lower()
    return host in {"xiaohongshu.com", "xhslink.com", "rednote.com"} or host.endswith((".xiaohongshu.com", ".xhslink.com", ".rednote.com"))


def _is_phncdn_url(url):
    host = urlparse(url).netloc.lower()
    return host.endswith(".phncdn.com") or host.endswith(".phncdn.net")


def _expand_redirect_url(url):
    try:
        request = Request(url, headers=_http_headers(url))
        with urlopen(request, timeout=8) as response:
            return response.geturl() or url
    except Exception:
        return url


def _is_twitter_info(info):
    key = (info.get("extractor_key") or info.get("extractor") or "").lower()
    return "twitter" in key or "x.com" in key


def _http_headers(url, cookie_header="", referer_header="", user_agent=""):
    headers = {
        "User-Agent": user_agent or "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    }
    if _is_xiaohongshu_url(url):
        if not user_agent or "Android" in user_agent or "Mobile" in user_agent:
            headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        headers["Referer"] = "https://www.xiaohongshu.com/"
        headers["Origin"] = "https://www.xiaohongshu.com"
        headers["Accept"] = "*/*"
    if _is_twitter_url(url):
        host = urlparse(url).netloc.lower()
        headers["Referer"] = "https://x.com/" if host == "x.com" or host.endswith(".x.com") else "https://twitter.com/"
        headers["Origin"] = headers["Referer"].rstrip("/")
    if _is_threads_url(url):
        headers["User-Agent"] = _desktop_user_agent()
        headers["Referer"] = referer_header or "https://www.threads.com/"
        headers["Origin"] = "https://www.threads.com"
    if _is_bilibili_url(url):
        headers["User-Agent"] = _desktop_user_agent()
        headers["Referer"] = "https://www.bilibili.com/"
        headers["Origin"] = "https://www.bilibili.com"
    if _is_weibo_url(url):
        headers["User-Agent"] = (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/122.0.0.0 Safari/537.36"
        )
        headers["Referer"] = referer_header or "https://weibo.com/"
        headers["Origin"] = "https://weibo.com"
    if _is_douyin_url(url):
        if not user_agent:
            headers["User-Agent"] = (
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/122.0.0.0 Mobile Safari/537.36"
            )
        headers["Referer"] = "https://www.douyin.com/"
        headers["Origin"] = "https://www.douyin.com"
    if _is_ixigua_url(url):
        headers["Referer"] = "https://www.ixigua.com/"
        headers["Origin"] = "https://www.ixigua.com"
    if _is_pornhub_url(url) or _is_phncdn_url(url):
        if not user_agent:
            headers["User-Agent"] = (
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/122.0.0.0 Mobile Safari/537.36"
            )
        parsed = urlparse(url)
        if _is_pornhub_url(url):
            origin = f"{parsed.scheme or 'https'}://{parsed.netloc}"
        else:
            origin = "https://www.pornhub.org"
        headers["Referer"] = referer_header or origin + "/"
        headers["Origin"] = origin
        headers["Accept"] = "*/*"
    if referer_header and not _is_xiaohongshu_url(url):
        headers["Referer"] = referer_header
    return headers


def _write_cookie_file(url, cookie_header):
    if not cookie_header:
        return ""

    if cookie_header.lstrip().startswith("# Netscape HTTP Cookie File"):
        fd, path = tempfile.mkstemp(prefix="media_downloader_cookies_", suffix=".txt")
        with os.fdopen(fd, "w", encoding="utf-8") as file:
            file.write(cookie_header)
            if not cookie_header.endswith("\n"):
                file.write("\n")
        return path

    cookies = []
    for raw_item in cookie_header.split(";"):
        item = raw_item.strip()
        if not item or "=" not in item:
            continue
        name, value = item.split("=", 1)
        name = name.strip()
        value = value.strip()
        if name:
            cookies.append((name, value))

    if not cookies:
        return ""

    fd, path = tempfile.mkstemp(prefix="media_downloader_cookies_", suffix=".txt")
    with os.fdopen(fd, "w", encoding="utf-8") as file:
        file.write("# Netscape HTTP Cookie File\n")
        for domain in _cookie_domains_for_url(url):
            for name, value in cookies:
                file.write(f"{domain}\tTRUE\t/\tFALSE\t0\t{name}\t{value}\n")
    return path


def _desktop_user_agent():
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"


def _cookie_domains_for_url(url):
    host = urlparse(url).netloc.lower().split(":")[0]
    if "xiaohongshu.com" in host or "xhslink.com" in host or "rednote.com" in host:
        return [".xiaohongshu.com", ".xhslink.com", ".rednote.com"]
    if host.endswith("youtube.com") or host == "youtu.be" or host.endswith("googlevideo.com"):
        return [".youtube.com", ".google.com"]
    if host == "b23.tv" or host.endswith(".b23.tv") or host == "bilibili.com" or host.endswith(".bilibili.com"):
        return [".bilibili.com", ".b23.tv"]
    if host == "x.com" or host.endswith(".x.com") or host == "twitter.com" or host.endswith(".twitter.com"):
        return [".x.com", ".twitter.com"]
    if host == "threads.com" or host.endswith(".threads.com") or host == "threads.net" or host.endswith(".threads.net") or host.endswith(".cdninstagram.com") or host.endswith(".fbcdn.net") or host.endswith(".fbsbx.com"):
        return [".threads.com", ".threads.net", ".instagram.com", ".cdninstagram.com", ".fbcdn.net", ".fbsbx.com"]
    if host == "weibo.com" or host.endswith(".weibo.com") or host == "weibo.cn" or host.endswith(".weibo.cn"):
        return [".weibo.com", ".weibo.cn", ".sina.com.cn"]
    if host == "douyin.com" or host.endswith(".douyin.com") or host == "iesdouyin.com" or host.endswith(".iesdouyin.com"):
        return [".douyin.com", ".iesdouyin.com"]
    if host == "pornhub.com" or host.endswith(".pornhub.com") or host == "pornhub.org" or host.endswith(".pornhub.org") or host.endswith(".phncdn.com") or host.endswith(".phncdn.net"):
        return [".pornhub.com", ".pornhub.org", ".phncdn.com", ".phncdn.net"]
    if host.startswith("www."):
        host = host[4:]
    if host.startswith("m."):
        host = host[2:]
    return ["." + host] if host else []


def _attempt_error(url, attempt, message):
    if not isinstance(attempt, dict):
        attempt = {"extractor_args": attempt}
    args_label = json.dumps(
        {
            "extractor_args": attempt.get("extractor_args") or "default",
            "format": attempt.get("format") or "default",
            "use_cookie": attempt.get("use_cookie", True),
        },
        ensure_ascii=False,
        sort_keys=True,
    )
    return f"{url} [{args_label}]: {message}"


def _delete_file(path):
    if not path:
        return
    try:
        os.remove(path)
    except OSError:
        pass


def _human_size(bytes_value):
    value = float(bytes_value)
    units = ["B", "KB", "MB", "GB"]
    unit_index = 0
    while value >= 1024 and unit_index < len(units) - 1:
        value /= 1024
        unit_index += 1
    if unit_index == 0:
        return f"{int(value)} {units[unit_index]}"
    return f"{value:.1f} {units[unit_index]}"


def _extractor_arg_candidates(url):
    if _is_twitter_url(url):
        return [
            None,
            {"twitter": {"api": ["legacy"]}},
            {"twitter": {"api": ["syndication"]}},
        ]
    if _is_youtube_url(url):
        return [
            {"youtube": {"player_client": ["tv_embedded"]}},
        ]
    return [None]


def _format_attempts(url, cookie_header=""):
    if _is_weibo_url(url):
        attempts = [{"extractor_args": args, "use_cookie": True} for args in _extractor_arg_candidates(url)]
        guest_cookie = _weibo_guest_cookie_header(url) if not cookie_header else ""
        if guest_cookie:
            attempts.append({"extractor_args": None, "cookie_header": guest_cookie, "use_cookie": True})
        return attempts

    if _is_bilibili_url(url):
        return [
            {"extractor_args": None, "use_cookie": True},
            {"extractor_args": None, "use_cookie": False},
        ]

    return [{"extractor_args": args, "use_cookie": True} for args in _extractor_arg_candidates(url)]


def _download_attempts(url, cookie_header=""):
    if _is_weibo_url(url):
        attempts = [{"extractor_args": args, "use_cookie": True} for args in _extractor_arg_candidates(url)]
        guest_cookie = _weibo_guest_cookie_header(url) if not cookie_header else ""
        if guest_cookie:
            attempts.append({"extractor_args": None, "cookie_header": guest_cookie, "use_cookie": True})
        return attempts

    if _is_bilibili_url(url):
        return [
            {"extractor_args": None, "format": _bilibili_format_selector(), "use_cookie": True},
            {"extractor_args": None, "format": "best", "use_cookie": True},
            {"extractor_args": None, "use_cookie": True},
            {"extractor_args": None, "format": _bilibili_format_selector(), "use_cookie": False},
            {"extractor_args": None, "format": "best", "use_cookie": False},
            {"extractor_args": None, "use_cookie": False},
        ]

    if not _is_youtube_url(url):
        return [{"extractor_args": args, "use_cookie": True} for args in _extractor_arg_candidates(url)]

    attempts = []
    for args in _extractor_arg_candidates(url):
        attempts.append(
            {
                "extractor_args": args,
                "format": _youtube_format_selector(),
                "use_cookie": True,
            }
        )
    return attempts


def _weibo_guest_cookie_header(url):
    try:
        visitor_url = "https://passport.weibo.com/visitor/genvisitor"
        request = Request(
            visitor_url,
            data=urlencode({
                "cb": "gen_callback",
                "fp": json.dumps({
                    "os": "1",
                    "browser": "Chrome122,0,0,0",
                    "fonts": "undefined",
                    "screenInfo": "1920*1080*24",
                    "plugins": "",
                }, separators=(",", ":")),
            }).encode("utf-8"),
            headers={
                "Referer": "https://weibo.com/",
                "User-Agent": _http_headers(url).get("User-Agent", ""),
                "Content-Type": "application/x-www-form-urlencoded",
            },
        )
        with urlopen(request, timeout=8) as response:
            body = response.read().decode("utf-8", "ignore")
        payload = body[body.find("(") + 1: body.rfind(")")]
        data = json.loads(payload).get("data") or {}
        tid = data.get("tid")
        if not tid:
            return ""
        confidence = data.get("confidence", 100)
        w = 3 if data.get("new_tid") else 2
        incarnate_url = (
            "https://passport.weibo.com/visitor/visitor"
            f"?a=incarnate&t={tid}&w={w}&c={int(confidence):03d}"
            "&gc=&cb=cross_domain&from=weibo&_rand=0.1"
        )
        request = Request(
            incarnate_url,
            headers={
                "Referer": "https://weibo.com/",
                "User-Agent": _http_headers(url).get("User-Agent", ""),
            },
        )
        with urlopen(request, timeout=8) as response:
            cookies = response.headers.get_all("Set-Cookie") or []
        pairs = []
        for item in cookies:
            pair = item.split(";", 1)[0].strip()
            if pair and "=" in pair:
                pairs.append(pair)
        return "; ".join(pairs)
    except Exception:
        return ""


class _YdlLogger:
    def __init__(self, messages):
        self.messages = messages

    def debug(self, message):
        msg = str(message)
        if any(x in msg for x in ["[XiaoHongShu]", "[rednote]", "Downloading", "HTTP", "redirect", "403", "URL", "page", "extractor"]):
            self._append("[DEBUG] " + msg)

    def warning(self, message):
        self._append("[WARN] " + str(message))

    def error(self, message):
        self._append("[ERROR] " + str(message))

    def _append(self, message):
        if message:
            self.messages.append(str(message))


def _compose_error(error, log_messages):
    details = [error]
    details.extend(message for message in log_messages[-40:] if message and message not in details)
    return "\n".join(details)


def _remove_stale_partial_files(output_dir):
    for filename in os.listdir(output_dir):
        if not (filename.endswith(".part") or filename.endswith(".ytdl")):
            continue
        filepath = os.path.join(output_dir, filename)
        if os.path.isfile(filepath):
            try:
                os.remove(filepath)
            except OSError:
                pass


def _append_candidate(candidates, filepath):
    if _is_readable_file(filepath) and filepath not in candidates:
        candidates.append(filepath)


def _is_readable_file(filepath):
    return bool(filepath) and os.path.isfile(filepath) and os.path.getsize(filepath) > 0


def _extension(filepath):
    return os.path.splitext(filepath or "")[1].lower().lstrip(".")


def _is_video_file(filepath):
    return _extension(filepath) in {"mp4", "m4v", "webm", "mkv"}


def _is_audio_file(filepath):
    return _extension(filepath) in {"m4a", "aac", "mp3", "opus"}


def _write_progress(progress_path, snapshot):
    if not progress_path:
        return

    total = snapshot.get("total_bytes") or 0
    downloaded = snapshot.get("downloaded_bytes") or 0
    progress_value = snapshot.get("progress")
    if progress_value is None and total > 0:
        progress_value = max(0, min(1, downloaded / total))
    elif progress_value is None:
        progress_value = 0

    payload = {
        "status": snapshot.get("status", ""),
        "filename": snapshot.get("filename", ""),
        "downloaded_bytes": downloaded,
        "total_bytes": total,
        "progress": progress_value,
        "speed": snapshot.get("speed") or 0,
        "eta": snapshot.get("eta") or 0,
    }
    temp_path = progress_path + ".tmp"
    with open(temp_path, "w", encoding="utf-8") as file:
        json.dump(payload, file, ensure_ascii=False)
    os.replace(temp_path, progress_path)
