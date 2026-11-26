import os, urllib.request
from concurrent.futures import ThreadPoolExecutor

# Bundle essential offline Surahs for both Sheikh Al-Sudais and Sheikh Al-Minshawi inside the APK (~20 MB total)
# Users can download any of the remaining 114 Surahs with one tap inside the app!
SURAH_NUMS = [1, 36, 56, 67, 112, 113, 114]
TARGETS = {
    'sudais': [
        'https://server11.mp3quran.net/sds/',
        'https://download.quranicaudio.com/quran/abdurrahmaan_as-sudays/'
    ],
    'minshawi': [
        'https://server10.mp3quran.net/minsh/',
        'https://download.quranicaudio.com/quran/muhammad_siddeeq_al-minshaawee/'
    ]
}

tasks = []
for rec, mirrors in TARGETS.items():
    os.makedirs(f'app/src/main/assets/audio/{rec}', exist_ok=True)
    for num in SURAH_NUMS:
        tasks.append((rec, f'{num:03d}', mirrors))

def dl(item):
    rec, s_pad, mirrors = item
    out = f'app/src/main/assets/audio/{rec}/{s_pad}.mp3'
    if os.path.exists(out) and os.path.getsize(out) > 4096:
        return True
    for base in mirrors:
        try:
            req = urllib.request.Request(base + s_pad + '.mp3', headers={'User-Agent': 'QuranAudioBuilder/1.0'})
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = resp.read()
            if len(data) > 4096:
                with open(out, 'wb') as f:
                    f.write(data)
                print(f'Bundled {rec}/{s_pad}.mp3 ({len(data)//1024} KB)')
                return True
        except Exception:
            continue
    return False

with ThreadPoolExecutor(max_workers=6) as ex:
    res = list(ex.map(dl, tasks))
print(f'Bundled {sum(res)}/{len(tasks)} offline MP3 surahs.')
