"""Recorrido acotado del lanzador/JAR. Solo crea una carpeta temporal propia; sin SMTP."""
import argparse
import html
import http.cookiejar
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

RAIZ = Path(__file__).resolve().parents[1]

class SinRedireccion(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def pedir(opener, url, datos=None):
    body = None if datos is None else urllib.parse.urlencode(datos).encode()
    try:
        respuesta = opener.open(url, body, timeout=3)
    except urllib.error.HTTPError as error:
        respuesta = error
    with respuesta:
        return respuesta.code, respuesta.headers, respuesta.read().decode('utf-8')

def login(base, password):
    opener = urllib.request.build_opener(SinRedireccion(), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    codigo, _, pagina = pedir(opener, base + '/login')
    token = re.search(r'name="_csrf"[^>]*value="([^"]+)"', pagina)
    assert codigo == 200 and token, 'Formulario de login con CSRF'
    codigo, cabeceras, _ = pedir(opener, base + '/login', {'username':'admin-demo@example.test', 'password':password, '_csrf':html.unescape(token[1])})
    assert codigo == 302 and cabeceras['Location'].endswith('/inicio'), 'Login conserva la password original'
    codigo, _, pagina = pedir(opener, base + '/usuarios')
    assert codigo == 200 and pagina.count('admin-demo@example.test') == 1, 'Un unico administrador, acceso ADMIN'
    return True

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--java-home', required=True)
    ap.add_argument('--pwsh', default=shutil.which('pwsh'))
    args = ap.parse_args()
    if not args.pwsh:
        raise RuntimeError('Indicar --pwsh con PowerShell 7 disponible')
    artefactos = RAIZ / 'Citas/target'
    evidencia = artefactos / '04-lanzador.json'
    original, nueva = secrets.token_urlsafe(24), secrets.token_urlsafe(24)
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0))
        puerto = s.getsockname()[1]
    registro = artefactos / 'demo-local' / f'proceso-{puerto}.json'
    assert not registro.exists(), 'Puerto de prueba sin registro previo'
    recorridos = []
    with tempfile.TemporaryDirectory(prefix='citas-04-') as tmp:
        temporal = Path(tmp)
        datos = temporal / 'datos con espacios'
        otra = temporal / 'otra carpeta'
        otra.mkdir()
        # Se suministra una ruta RELATIVA A LA RAIZ y se ejecuta desde otra carpeta.
        relativa = os.path.relpath(datos, RAIZ)
        for vuelta in range(2):
            log = artefactos / f'04-lanzador-{vuelta + 1}.log'
            entorno = {k:v for k,v in os.environ.items() if not k.startswith(('SPRING_', 'DEMO_ADMIN_')) and k != 'CITAS_DATOS'}
            entorno['DEMO_ADMIN_PASSWORD'] = original if vuelta == 0 else nueva
            comando = [args.pwsh, '-NoProfile', '-File', str(RAIZ / 'scripts/ejecutar-demo-local.ps1'), '-JavaHome', args.java_home,
                       '-Puerto', str(puerto), '-DirectorioDatos', relativa]
            if vuelta:
                comando.append('-RecuperacionLocal')
            with log.open('w', encoding='utf-8') as salida:
                lanzador = subprocess.Popen(comando, cwd=otra, env=entorno, stdout=salida, stderr=subprocess.STDOUT)
                propio = None
                try:
                    limite = time.monotonic() + 50
                    base = f'http://127.0.0.1:{puerto}'
                    while time.monotonic() < limite:
                        assert lanzador.poll() is None, 'Lanzador sigue activo; revisar log local'
                        if registro.exists():
                            try:
                                propio = json.loads(registro.read_text(encoding='utf-8-sig'))
                                if pedir(urllib.request.build_opener(), base + '/login')[0] == 200:
                                    break
                            except (OSError, ValueError):
                                pass
                        time.sleep(0.2)
                    else:
                        raise AssertionError('La demo no estuvo disponible en 50 segundos')
                    assert Path(propio['datos']).resolve() == datos.resolve(), 'Ruta estable desde la raiz'
                    assert (datos / 'hospital.mv.db').is_file(), 'Archivo persistente fuera de target'
                    assert login(base, original)
                    recorridos.append({'arranque':vuelta+1, 'pid_java':propio['pid'], 'login_admin':True, 'admin_unico':True,
                                       'ruta_desde_raiz':True, 'recuperacion_local':bool(vuelta)})
                finally:
                    if propio is not None and lanzador.poll() is None:
                        detener = subprocess.run([args.pwsh, '-NoProfile', '-File', str(RAIZ / 'scripts/detener-demo-local.ps1'), '-Puerto', str(puerto)],
                                                 cwd=otra, capture_output=True, text=True, timeout=20)
                        assert detener.returncode == 0, 'Parada valida exclusivamente su JVM; revisar identidad si falla'
                    assert lanzador.wait(timeout=20) == 0, 'Lanzador termino correctamente'
                assert not registro.exists(), 'Registro propio retirado al terminar'
                texto = log.read_text(encoding='utf-8', errors='replace')
                assert original not in texto and nueva not in texto, 'Sin passwords en logs'
                assert 'persistencia-local' in texto, 'Perfil persistente activo'
                assert ('Successfully applied 1 migration' if vuelta == 0 else 'No migration necessary') in texto, 'V1 unica y segundo arranque sin migrar de nuevo'
        assert recorridos[0]['pid_java'] != recorridos[1]['pid_java'], 'Dos procesos distintos'
    resumen = {'recorridos':recorridos, 'carpeta_temporal_eliminada_tras_parar':True, 'password_original_conservada':True,
               'puerto_loopback':puerto, 'parada_propia_verificada':True, 'smtp_no_ejecutado':True}
    evidencia.write_text(json.dumps(resumen, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Lanzador/JAR: dos arranques, ruta estable, V1 unica, login/admin conservados y parada propia correctos.')

if __name__ == '__main__':
    main()
