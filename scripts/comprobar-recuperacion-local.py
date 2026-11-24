"""Recorrido HTTP/SMTP real contra dos procesos loopback ya iniciados. Sin secretos en salida."""
import argparse
import http.cookiejar
import json
import re
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from html.parser import HTMLParser

class Inputs(HTMLParser):
    def __init__(self):
        super().__init__()
        self.values = {}
    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == 'input' and 'name' in values:
            self.values[values['name']] = values.get('value', '')

def client():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

def request(opener, url, data=None):
    body = urllib.parse.urlencode(data).encode() if data is not None else None
    with opener.open(url, data=body, timeout=10) as response:
        return response.status, response.geturl(), response.read().decode('utf-8')

def csrf(opener, url):
    _, _, body = request(opener, url)
    parser = Inputs()
    parser.feed(body)
    assert parser.values.get('_csrf'), 'Formulario sin CSRF'
    return parser.values['_csrf']

def loopback(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != 'http' or parsed.hostname not in ('127.0.0.1', 'localhost') or parsed.username or parsed.password:
        raise ValueError('Solo se permite HTTP loopback')
    return url.rstrip('/')

def run(app, mailbox):
    global etapa
    app, mailbox = loopback(app), loopback(mailbox)
    anon, session, fresh = client(), client(), client()
    identificador = secrets.token_hex(8)
    email = 'recuperacion-' + identificador + '@example.test'
    anterior, nueva = secrets.token_urlsafe(24), secrets.token_urlsafe(24)
    etapa = 'registro sintetico'
    _, url, _ = request(anon, app + '/registro', {'_csrf': csrf(anon, app + '/registro'),
        'nombre': 'Paciente ficticio', 'cedula': ''.join(secrets.choice('0123456789') for _ in range(9)), 'correo': email,
        'contacto': 'Contacto ficticio', 'password': anterior})
    assert url.endswith('/login?registroExitoso')
    etapa = 'login anterior y sesion real'
    _, url, _ = request(session, app + '/login', {'_csrf': csrf(session, app + '/login'), 'username': email, 'password': anterior})
    assert url.endswith('/inicio')
    _, url, _ = request(session, app + '/perfil')
    assert url.endswith('/perfil')
    etapa = 'solicitud generica'
    _, _, body = request(anon, app + '/recuperar-password', {'_csrf': csrf(anon, app + '/recuperar-password'), 'correo': email})
    assert 'recibiras un correo' in body
    assert '#token=' not in body
    etapa = 'recepcion SMTP en Mailpit'
    message_id = None
    for _ in range(40):
        data = json.loads(request(anon, mailbox + '/api/v1/messages')[2])
        for message in data.get('messages', []):
            if any(recipient.get('Address') == email for recipient in message.get('To', [])):
                message_id = message['ID']
                break
        if message_id:
            break
        time.sleep(0.1)
    assert message_id is not None
    mensaje = json.loads(request(anon, mailbox + '/api/v1/message/' + message_id)[2])
    match = re.search(r'http://127\.0\.0\.1:[0-9]+/cambiar-password#token=([A-Za-z0-9_-]{43})', mensaje['Text'])
    assert match is not None
    token = match.group(1)
    link = urllib.parse.urlsplit(match.group(0))
    assert link.scheme + '://' + link.netloc == app
    assert token not in body
    etapa = 'GET formulario sin consumo ni token en HTML'
    # Un navegador no envía el fragmento. El JS propio copia el token a un campo POST.
    formulario = app + link.path
    _, _, body = request(anon, formulario)
    assert token not in body and 'name="nuevaPassword"' in body
    etapa = 'POST de restablecimiento'
    _, url, _ = request(anon, formulario, {'_csrf': csrf(anon, formulario), 'token': token, 'nuevaPassword': nueva})
    assert url.endswith('/login?passwordCambiada')
    etapa = 'sesion anterior revocada'
    _, url, _ = request(session, app + '/perfil')
    assert '/login' in url
    etapa = 'password anterior rechazada'
    _, url, _ = request(fresh, app + '/login', {'_csrf': csrf(fresh, app + '/login'), 'username': email, 'password': anterior})
    assert url.endswith('/login?error')
    etapa = 'password nueva valida'
    _, url, _ = request(fresh, app + '/login', {'_csrf': csrf(fresh, app + '/login'), 'username': email, 'password': nueva})
    assert url.endswith('/inicio')
    etapa = 'token reutilizado rechazado'
    _, url, body = request(anon, formulario, {'_csrf': csrf(anon, formulario), 'token': token, 'nuevaPassword': secrets.token_urlsafe(24)})
    assert url.endswith('/cambiar-password') and 'ya no esta disponible' in body
    return {'resultado': 'paso', 'smtp_real': True, 'mailpit_mensaje_verificado': True,
        'get_no_refleja_token': True, 'reset_real': True, 'sesion_real_revocada': True,
        'password_anterior_rechazada': True, 'nuevo_login_valido': True, 'reutilizacion_rechazada': True,
        'javascript_ejecutado_en_navegador': False}

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--app', default='http://127.0.0.1:18082')
    parser.add_argument('--buzon', default='http://127.0.0.1:18025')
    args = parser.parse_args()
    etapa = 'inicio'
    try:
        print(json.dumps(run(args.app, args.buzon), ensure_ascii=False))
    except Exception:
        print(json.dumps({'resultado': 'fallo', 'etapa': etapa, 'detalles_sensibles_omitidos': True}))
        sys.exit(1)
