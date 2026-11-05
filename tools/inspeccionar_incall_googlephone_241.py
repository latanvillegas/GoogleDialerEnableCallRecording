#!/usr/bin/env python3
"""Auditoria de controladores de pantalla de llamada para Google Phone 241.

Solo lectura del APK. Extrae clases DEX/smali relevantes al dialpad y a
pantallas entrantes/activas, nunca contactos, preferencias ni datos privados.

Requiere Python 3 y apktool. No requiere root, ADB ni Shizuku.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from datetime import datetime, timezone
from pathlib import Path

EXPECTED_VERSION = "241.0.991235506"
RESOURCES = {
    "layout/fragment_incoming_call": "0x7f0e0138",
    "layout/legacy_fragment_incoming_call": "0x7f0e0162",
    "layout/incall_activity_layout": "0x7f0e0149",
    "layout/xatu_in_call_fragment": "0x7f0e030a",
    "layout/xatu_in_call_toolbar": "0x7f0e030b",
    "id/answer_method_container": "0x7f0b008c",
    "id/background_fragment_container": "0x7f0b00cb",
    "id/btn_audio_route": "0x7f0b0111",
    "id/btn_dialpad": "0x7f0b0114",
    "id/btn_end_call": "0x7f0b0115",
    "id/btn_mute": "0x7f0b0116",
    "id/contactgrid_avatar": "0x7f0b01ee",
    "id/contactgrid_contact_name": "0x7f0b01f7",
    "id/foreground_container": "0x7f0b03c8",
    "id/incall_fragment_container": "0x7f0b0423",
    "attr/colorEndButton": "0x7f040176",
    "attr/colorIconInCall": "0x7f040181",
}

RESOURCE_TERMS = sorted(set(
    [name.rsplit("/", 1)[1] for name in RESOURCES] + list(RESOURCES.values())
), key=len, reverse=True)

DIRECT_CLASS_RE = re.compile(
    r"(?:^|[/\\])(?:[\w$]*InCallActivity[\w$]*|[\w$]*InCallServiceImpl[\w$]*|"
    r"[\w$]*IncomingCall[\w$]*|[\w$]*AnswerFragment[\w$]*|"
    r"[\w$]*InCallFragment[\w$]*|[\w$]*Xatu[\w$]*)\.smali$",
    re.IGNORECASE,
)
SOURCE_TERMS = [
    "com/android/dialer/incall/activity/ui/InCallActivity",
    "com/android/incallui/InCallServiceImpl",
    "fragment_incoming_call", "legacy_fragment_incoming_call",
    "xatu_in_call_fragment", "incall_activity_layout", "btn_end_call",
    "answer_method_container", "background_fragment_container",
    "contactgrid_avatar", "foreground_container", "InCallScreen",
]

MIN_SCORE = 30
MAX_SOURCES = 100
MAX_TOTAL_BYTES = 7_000_000
MAX_SINGLE_BYTES = 350_000


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for block in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def selected_apk(explicit: str | None) -> Path:
    if explicit:
        return Path(explicit).expanduser().resolve()
    paths = [
        Path("/storage/emulated/0/Download/Teléfono.apk"),
        Path.home() / "storage/downloads/Teléfono.apk",
    ]
    for path in paths:
        if path.is_file():
            return path.resolve()
    raise FileNotFoundError(
        "No se encontró Teléfono.apk. Usa --apk '/storage/emulated/0/Download/Teléfono.apk'"
    )


def archive_dex_info(path: Path) -> list[dict]:
    with zipfile.ZipFile(path) as z:
        result = [
            {"nombre": i.filename, "bytes": i.file_size}
            for i in z.infolist()
            if re.fullmatch(r"classes\d*\.dex", i.filename)
        ]
    return sorted(result, key=lambda x: x["nombre"])


def header_report(apk: Path) -> dict:
    info = {
        "archivo": apk.name,
        "bytes": apk.stat().st_size,
        "sha256": sha256(apk),
        "classes_dex": archive_dex_info(apk),
        "fecha_utc": datetime.now(timezone.utc).isoformat(),
        "id_recursos_originales_confirmados": RESOURCES,
    }
    if not info["classes_dex"]:
        raise ValueError("La APK no contiene classes.dex")
    aapt = shutil.which("aapt2") or shutil.which("aapt")
    if aapt:
        try:
            x = subprocess.run([aapt, "dump", "badging", str(apk)], capture_output=True,
                               text=True, errors="replace", timeout=40)
            match = re.search(r"^package:\s*(.+)$", x.stdout, re.MULTILINE)
            info["identidad_apk"] = match.group(1) if match else x.stderr.strip()[:500]
            if match and EXPECTED_VERSION not in match.group(1):
                print("AVISO: la version no coincide con la analizada.", file=sys.stderr)
        except (OSError, subprocess.TimeoutExpired) as e:
            info["identidad_apk_error"] = str(e)
    return info


def smali_folders(decoded: Path) -> list[Path]:
    return sorted(p for p in decoded.iterdir()
                  if p.is_dir() and re.fullmatch(r"smali(?:_classes\d+)?", p.name))


def collect(decoded: Path, meta: dict, target: Path, cap: int) -> None:
    dirs = smali_folders(decoded)
    if not dirs:
        raise RuntimeError("No se encontraron directorios smali. Revisa si apktool extrajo DEX")

    candidates: list[dict] = []
    total_smali = 0
    for root in dirs:
        print("Analizando", root.name, "...", flush=True)
        for dirpath, _, names in os.walk(root):
            for filename in names:
                if not filename.endswith(".smali"):
                    continue
                total_smali += 1
                path = Path(dirpath) / filename
                if filename == "R.smali" or filename.startswith("R$"):
                    continue
                try:
                    raw = path.read_bytes()
                except OSError:
                    continue
                text = raw.decode("utf-8", errors="replace")
                relative = path.relative_to(decoded).as_posix()
                direct = DIRECT_CLASS_RE.search(relative) is not None
                count = sum(text.count(s) for s in SOURCE_TERMS)
                resource_hits = [s for s in RESOURCE_TERMS if s in text]
                if not direct and count == 0 and not resource_hits:
                    continue
                # Las coincidencias de recurso tienen mas peso que un nombre generico.
                score = min(count, 20) * 8 + min(len(resource_hits), 20) * 25
                if direct:
                    score += 200 if "incall" in filename.lower() else 110
                if "com/android/dialer/incall/" in relative:
                    score += 150
                if "com/android/incallui/" in relative:
                    score += 140
                if score < MIN_SCORE:
                    continue
                declared = re.search(r"^\.class\s+[^\n]*?(L[^;]+;)", text, re.MULTILINE)
                method_names = re.findall(r"^\.method\s+(.+)$", text, re.MULTILINE)
                lines = text.splitlines()
                evidence = []
                for index, line in enumerate(lines):
                    if any(s in line for s in RESOURCE_TERMS if len(s) > 5) or any(s in line for s in SOURCE_TERMS):
                        evidence.append({
                            "linea": index + 1,
                            "texto": line.strip()[:240],
                        })
                    if len(evidence) == 35:
                        break
                candidates.append({
                    "path": relative,
                    "clase": declared.group(1) if declared else None,
                    "score": score,
                    "tamano": len(raw),
                    "metodos": method_names[:120],
                    "coincidencias_recursos": resource_hits,
                    "evidencia": evidence,
                })

    candidates.sort(key=lambda x: (-x["score"], x["path"]))
    selected = []
    bytes_total = 0
    for row in candidates:
        if len(selected) >= cap:
            break
        size = row["tamano"]
        if size > MAX_SINGLE_BYTES or bytes_total + size > MAX_TOTAL_BYTES:
            row["omitido"] = "Limite de tamaño por archivo o conjunto"
            continue
        selected.append(row)
        bytes_total += size

    meta.update({
        "directorios_smali": [x.name for x in dirs],
        "archivos_smali_escaneados": total_smali,
        "candidatos_encontrados": len(candidates),
        "seleccionados": len(selected),
        "bytes_smali_seleccionados": bytes_total,
        "clases": candidates[:400],
        "clases_omitidas_fuera_de_reporte": max(0, len(candidates) - 400),
    })

    lines = [
        "AUDITORIA DE PANTALLAS DE LLAMADA GOOGLE PHONE 241",
        "===============================================",
        "APK: " + meta["archivo"],
        "SHA256: " + meta["sha256"],
        "Clases smali analizadas: " + str(total_smali),
        "Candidatas: " + str(len(candidates)),
        "Archivos adjuntos: " + str(len(selected)),
        "",
        "RECURSOS IDENTIFICADOS EN APK ORIGINAL:",
    ]
    lines += [f"{key}={value}" for key, value in RESOURCES.items()]
    lines.append("\nCLASES RELEVANTES:")
    for row in candidates[:90]:
        lines.append(f"{row['score']:4d} {row['path']} ({row['tamano']} bytes)")
        for item in row["evidencia"][:5]:
            lines.append(f"       L{item['linea']}: {item['texto']}")
    lines += [
        "", "NOTA: Este reporte contiene bytecode de la APK, no configuraciones",
        "personales, llamadas, mensajes ni contactos del dispositivo.",
    ]

    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=7) as z:
        z.writestr("informe.txt", "\n".join(lines) + "\n")
        z.writestr("informe.json", json.dumps(meta, ensure_ascii=False, indent=2))
        for row in selected:
            p = decoded / row["path"]
            z.write(p, arcname=row["path"])
    print("\n=== RESULTADO ===")
    print("Reporte:", target)
    print("Tamaño:", target.stat().st_size, "bytes")
    print("Clases relevantes:", len(candidates))
    print("Clases exportadas:", len(selected))
    if not selected:
        print("AVISO: No hubo clases exportables; revisa informe.txt")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", help="Ruta exacta a la APK ya parcheada")
    parser.add_argument("--out", default="/storage/emulated/0/Download/auditoria-incall-googlephone-241.zip",
                        help="ZIP de salida")
    parser.add_argument("--decoded", help="Carpeta ya decodificada con smali (no ejecutar apktool)")
    parser.add_argument("--max-classes", type=int, default=MAX_SOURCES, help="Limite de archivos smali")
    args = parser.parse_args()
    if not 1 <= args.max_classes <= 200:
        parser.error("--max-classes debe estar entre 1 y 200")
    try:
        apk = selected_apk(args.apk)
        if not apk.is_file():
            raise FileNotFoundError(apk)
        print("APK:", apk, flush=True)
        meta = header_report(apk)
        print("Dex:", len(meta["classes_dex"]), flush=True)
        if args.decoded:
            decoded = Path(args.decoded).expanduser().resolve()
            collect(decoded, meta, Path(args.out).expanduser(), args.max_classes)
        else:
            tool = shutil.which("apktool")
            if not tool:
                raise RuntimeError("apktool no está instalado. Debe estar disponible en PATH")
            with tempfile.TemporaryDirectory(prefix="gphone-visual-", dir=str(Path.home())) as tmp:
                decoded = Path(tmp) / "decoded"
                command = [tool, "d", "-f", "-r", "-o", str(decoded), str(apk)]
                print("Decodificando bytecode con apktool (solo lectura de APK)...", flush=True)
                with (Path(tmp) / "apktool.log").open("w", encoding="utf-8") as log:
                    result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
                if result.returncode != 0:
                    tail = (Path(tmp) / "apktool.log").read_text(errors="replace")[-4000:]
                    raise RuntimeError("apktool falló:\n" + tail)
                collect(decoded, meta, Path(args.out).expanduser(), args.max_classes)
        return 0
    except (RuntimeError, ValueError, OSError, zipfile.BadZipFile) as exc:
        print("ERROR:", exc, file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
