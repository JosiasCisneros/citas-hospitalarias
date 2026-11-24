"""Comprobacion HTTP de acceso/paciente; no acredita navegador, dialogos ni teclado."""
import argparse
import csv
import io
import html
import urllib.parse
import datetime
import importlib.util
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import time
import urllib.error
import urllib.request

RAIZ=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('recuperacion_http',RAIZ/'scripts/comprobar-recuperacion-local.py')
h=importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home',required=True)
    parser.add_argument('--jar',type=Path,default=RAIZ/'Citas/target/Citas-0.0.1-SNAPSHOT.jar')
    parser.add_argument('--prefijo-evidencia',default='06A-http')
    args=parser.parse_args()
    assert re.fullmatch(r'[A-Za-z0-9_-]+',args.prefijo_evidencia), 'Prefijo de evidencia simple'
    target=RAIZ/'Citas/target'
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0));puerto=sock.getsockname()[1]
    base=f'http://127.0.0.1:{puerto}'
    password=secrets.token_urlsafe(24)
    nueva=secrets.token_urlsafe(24)
    env={k:v for k,v in os.environ.items() if not k.startswith(('SPRING_','DEMO_ADMIN_'))}
    env.update(DEMO_ADMIN_NOMBRE='Administrador ficticio',DEMO_ADMIN_CEDULA='TEST-06A-ADMIN',DEMO_ADMIN_CORREO='admin-06a@example.test',DEMO_ADMIN_PASSWORD=password)
    comando=[str(Path(args.java_home)/'bin/java.exe'),'-jar',str(args.jar.resolve()),
        '--spring.profiles.active=demo-local','--server.address=127.0.0.1',f'--server.port={puerto}',
        '--spring.datasource.url=jdbc:h2:mem:acceso06A','--spring.jpa.hibernate.ddl-auto=create-drop',
        '--spring.jpa.show-sql=false','--logging.level.root=ERROR','--logging.level.org.hibernate.SQL=OFF']
    resultado={'navegador_visual':False,'smtp_ejecutado':False,'pantallas_http':[]}
    with (target/(args.prefijo_evidencia+'.log')).open('w',encoding='utf-8') as log:
        proceso=subprocess.Popen(comando,cwd=RAIZ/'Citas',env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            anon,paciente,admin=h.client(),h.client(),h.client()
            limite=time.monotonic()+40
            while time.monotonic()<limite:
                assert proceso.poll() is None,'La JVM debe seguir activa; revisar log local'
                try:
                    if h.request(anon,base+'/login')[0]==200:break
                except OSError:pass
                time.sleep(.2)
            else:raise AssertionError('Arranque no disponible en 40s')
            def get(cliente,ruta,destino=None):
                codigo,url,body=h.request(cliente,base+ruta)
                assert codigo==200 and url==base+(destino or ruta),ruta
                assert 'id="contenido"' in body and '<p>Citas hospitalarias</p>' in body,ruta
                assert not re.search(r'(?:src|href)="https?://',body),ruta
                if ruta.split('?')[0] in ['/citas/disponibles','/citas/mis-citas','/admin/citas','/admin/reportes','/disponibilidades','/disponibilidades/nueva']:
                    assert 'Hora de Costa Rica' not in body,ruta
                assert not any(texto in body for texto in ['El horario no se retiene:', 'Un horario pasado no puede', 'Los horarios pasados no se', 'id="ayuda-horario"', 'id="aviso-correo"', 'id="ayuda-nueva-password"', 'Desactivar conserva las citas de la cuenta.']),ruta
                ids=set(re.findall(r'\bid="([^"]+)"',body))
                for referencia in re.findall(r'aria-describedby="([^"]+)"',body):
                    assert set(referencia.split())<=ids,'Referencias ARIA con destino'
                for tabla in re.findall(r'<table[^>]*>(.*?)</table>',body,re.S):
                    assert '<caption' in tabla,'Tabla con titulo accesible'
                    for th in re.findall(r'<th\b([^>]*)>',tabla):assert 'scope="col"' in th
                resultado['pantallas_http'].append(ruta)
                return body
            def post(cliente,ruta,datos,formulario):
                return h.request(cliente,base+ruta,{'_csrf':h.csrf(cliente,base+formulario),**datos})
            for ruta in ['/login','/registro','/recuperar-password','/cambiar-password']:
                body=get(anon,ruta)
                assert 'public-shell' in body and 'navegacion-app' not in body
                campos=h.Inputs();campos.feed(body)
                assert campos.values.get('_csrf'),ruta
                ids=set(re.findall(r'\bid="([^"]+)"',body))
                for referencia in re.findall(r'aria-describedby="([^"]+)"',body):
                    assert set(referencia.split())<=ids,'Referencias ARIA con destino'
                if ruta in ['/login','/registro']:
                    assert 'Consulta y gestiona tus citas.' in body
                    assert 'Qué gusto verte' not in body and 'Empecemos' not in body
                if ruta=='/registro':assert 'ayuda-password' not in body
            body=get(anon,'/recuperar-password')
            assert 'no esta disponible en este entorno' in body and 'role="status"' in body
            body=get(anon,'/cambiar-password')
            assert re.search(r'id="restablecer"[^>]*disabled',body)
            assert 'token-recuperacion' in body and 'vuelve a abrir' in body
            with anon.open(base+'/cambiar-password') as response:
                assert "script-src 'self'" in response.headers['Content-Security-Policy']
                assert response.headers['Referrer-Policy']=='no-referrer'
                assert response.headers['Cache-Control']=='no-store'
            for recurso in ['css/header.css','css/contenido.css','css/footer.css','js/recuperacion.js']:
                assert h.request(anon,base+'/'+recurso)[0]==200
            for estado in ['error','logout','registroExitoso','passwordCambiada','sesionRestablecida']:
                body=get(anon,'/login?'+estado)
                assert ('role="alert"' if estado=='error' else 'role="status"') in body
            _,url,_=post(anon,'/registro',dict(nombre='Paciente ficticio',cedula='000000001',correo='paciente-06a@example.test',contacto='Contacto ficticio',password=password),'/registro')
            assert url.endswith('/login?registroExitoso')
            _,_,body=post(anon,'/registro',dict(nombre='Paciente ficticio',cedula='000000002',correo='paciente-06a@example.test',password=password),'/registro')
            assert 'role="alert"' in body and password not in body
            for cliente,correo in [(paciente,'paciente-06a@example.test'),(admin,'admin-06a@example.test')]:
                _,url,_=post(cliente,'/login',dict(username=correo,password=password),'/login')
                assert url==base+('/admin/citas' if cliente is admin else '/inicio')
            body=get(paciente,'/inicio')
            assert 'app-shell' in body and 'Administración' not in body and 'aria-current="page"' in body
            for ruta in ['/perfil','/medicos','/citas/disponibles','/citas/mis-citas']:
                get(paciente,ruta)
            try:h.request(paciente,base+'/usuarios');raise AssertionError('ADMIN debe quedar protegido')
            except urllib.error.HTTPError as exc:assert exc.code==403
            body=get(admin,'/inicio',destino='/admin/citas');assert 'Administración' in body and 'Mi atención' not in body and '<details>' in body and '<summary>Menú</summary>' in body
            get(admin,'/medicos/nuevo')
            post(admin,'/medicos/guardar',dict(nombre='Medico ficticio '+('Apellido de demostración '*4)+'<prueba>',especialidad='General'),'/medicos/nuevo')
            body=get(admin,'/medicos');medico=re.search(r'/medicos/editar/(\d+)',body)[1]
            body=get(admin,'/medicos/editar/'+medico)
            datos_medico=h.Inputs();datos_medico.feed(body)
            nombre_medico=datos_medico.values['nombre']+' revisado'
            _,url,_=post(admin,'/medicos/editar/'+medico,dict(nombre=nombre_medico,especialidad='General'),'/medicos/editar/'+medico)
            assert url.endswith('/medicos?actualizado')
            body=get(paciente,'/medicos')
            assert '/medicos/nuevo' not in body and '/medicos/editar/' not in body and '/medicos/desactivar/' not in body
            get(admin,'/disponibilidades/nueva')
            fecha=(datetime.date.today()+datetime.timedelta(days=3)).isoformat()
            post(admin,'/disponibilidades/guardar',dict(medicoId=medico,fecha=fecha,horaInicio='09:10',horaFin='10:10'),'/disponibilidades/nueva')
            body=get(admin,'/disponibilidades');disp=re.search(r'/disponibilidades/editar/(\d+)',body)[1]
            get(admin,'/disponibilidades/editar/'+disp)
            _,url,_=post(admin,'/disponibilidades/editar/'+disp,dict(fecha=fecha,horaInicio='09:10',horaFin='10:40'),'/disponibilidades/editar/'+disp)
            assert url.endswith('/disponibilidades?actualizado')
            body=get(paciente,'/disponibilidades');assert '/disponibilidades/editar/' not in body and '/disponibilidades/eliminar/' not in body
            fecha_libre=(datetime.date.fromisoformat(fecha)+datetime.timedelta(days=1)).isoformat()
            post(admin,'/disponibilidades/guardar',dict(medicoId=medico,fecha=fecha_libre,horaInicio='09:10',horaFin='10:10'),'/disponibilidades/nueva')
            body=get(admin,'/disponibilidades')
            filas=re.findall(r'<tr[^>]*>(.*?)</tr>',body,re.S)
            fila_libre=next(f for f in filas if datetime.date.fromisoformat(fecha_libre).strftime('%d/%m/%Y') in f)
            libre=re.search(r'/disponibilidades/eliminar/(\d+)',fila_libre)[1]
            _,url,_=post(admin,'/disponibilidades/eliminar/'+libre,{},'/disponibilidades');assert url.endswith('/disponibilidades?eliminado')
            assert '/disponibilidades/editar/'+libre+'"' not in get(admin,'/disponibilidades')
            body=get(admin,'/usuarios');usuario=re.search(r'/usuarios/editar/(\d+)',body)[1]
            get(admin,'/usuarios/editar/'+usuario)
            for ruta in ['/admin/citas','/admin/reportes']:get(admin,ruta)
            # El HTML incluye resumen/advertencia y todos los paneles abiertos sin JS.
            body=get(paciente,'/citas/disponibles');assert 'Medico ficticio' in body and '&lt;prueba&gt;' in body
            assert '<prueba>' not in body and 'accordion-collapse collapse show' in body and 'data-aviso-sin-js' in body
            assert '<dialog' in body and 'Revisar reserva' in body and 'Hora de Costa Rica' not in body
            for recurso in ['css/bootstrap.min.css','js/bootstrap.bundle.min.js','css/paciente.css','js/agenda.js']:
                assert h.request(paciente,base+'/'+recurso)[0]==200
            def reserva_html(html,inicio):
                for cabecera,contenido in re.findall(r'<form\b([^>]*data-revisar-reserva[^>]*)>(.*?)</form>',html,re.S):
                    # HTMLParser, sin regex para decodificar atributos escapados.
                    class Atributos(h.HTMLParser):
                        def handle_starttag(self,tag,attrs):self.attrs=dict(attrs)
                    atributos=Atributos();atributos.feed('<form '+cabecera+'>')
                    campos=h.Inputs();campos.feed(contenido)
                    if campos.values.get('horaInicio')==inicio:
                        d=atributos.attrs
                        assert d['data-inicio']==campos.values['horaInicio']
                        assert d['data-fecha']==datetime.date.fromisoformat(fecha).strftime('%d/%m/%Y')
                        assert '<prueba>' in d['data-medico'] and d['data-especialidad']=='General'
                        assert (datetime.datetime.strptime(d['data-fin'],'%H:%M')-datetime.datetime.strptime(d['data-inicio'],'%H:%M')).seconds==1800
                        assert campos.values.get('_csrf')
                        return d['action'],campos.values
                raise AssertionError('Horario esperado en el HTML')
            accion,resumen=reserva_html(body,'09:10')
            assert 'Aún no tienes citas' in get(paciente,'/citas/mis-citas')
            # Intercalado secuencial: otro paciente ocupa el horario despues de leer el resumen.
            post(anon,'/registro',dict(nombre='Paciente alterno ficticio',cedula='000000003',correo='ocupacion-06b@example.test',password=password),'/registro')
            otro=h.client();post(otro,'/login',dict(username='ocupacion-06b@example.test',password=password),'/login')
            _,url,_=post(otro,'/citas/reservar/'+disp,dict(horaInicio='09:10'),'/citas/disponibles');assert url.endswith('/citas/mis-citas')
            cita_otro=re.search(r'action="/citas/cancelar/(\d+)"',get(otro,'/citas/mis-citas'))[1]
            _,url,rechazo=h.request(paciente,base+accion,resumen)
            assert url.endswith('/citas/disponibles') and 'role="alert"' in rechazo and 'ya no esta disponible' in rechazo
            assert 'Aún no tienes citas' in get(paciente,'/citas/mis-citas')
            accion,campos=reserva_html(rechazo,'09:40')
            _,url,_=h.request(paciente,base+accion,campos);assert url.endswith('/citas/mis-citas')
            body=get(paciente,'/citas/mis-citas')
            assert body.count('class="tarjeta-cita"')==1 and '09:40' in body and '>Pendiente</span>' in body
            assert 'Conservar cita' in body and 'El horario volverá a quedar disponible.' in body
            # Volver a leer no cambia la cita; el cierre del dialogo se prueba aparte con JS simulado.
            assert '>Pendiente</span>' in get(paciente,'/citas/mis-citas')
            cancelar=re.search(r'action="(/citas/cancelar/\d+)"',body)[1]
            cita_propia=cancelar.rsplit('/',1)[1]
            revision=get(admin,'/admin/citas')
            assert 'data-titulo="¿Quieres confirmar esta cita?"' in revision
            assert 'data-mensaje="La cita pasará al estado Confirmada."' in revision
            assert 'data-estilo="primario"' in revision and 'data-accion="Confirmar cita"' in revision
            assert 'data-revisar-accion' in revision and 'data-aviso-sin-js' in revision
            assert re.search(r'<p[^>]*>ID '+medico+r'</p>',get(admin,'/medicos'))
            assert not re.search(r'<p[^>]*>ID '+medico+r'</p>',get(paciente,'/medicos'))
            perfil_admin=get(admin,'/perfil')
            cedula_admin=re.search(r'<input\b[^>]*id="cedula"[^>]*>',perfil_admin)[0]
            assert 'TEST-06A-ADMIN' in cedula_admin and 'pattern=' not in cedula_admin
            _,url,_=post(admin,'/admin/citas/confirmar/'+cita_propia,{},'/admin/citas');assert url.endswith('/admin/citas')
            assert '>Confirmada</span>' in get(paciente,'/citas/mis-citas')
            assert 'action="/admin/citas/confirmar/'+cita_propia+'"' not in get(admin,'/admin/citas')
            post(paciente,cancelar,{},'/citas/mis-citas')
            body=get(paciente,'/citas/mis-citas')
            assert body.count('class="tarjeta-cita"')==1 and '>Cancelada</span>' in body
            assert 'action="/citas/cancelar/' not in body
            reserva_html(get(paciente,'/citas/disponibles'),'09:40')
            for ruta in ['/admin/citas','/admin/reportes']:assert 'Medico ficticio' in get(admin,ruta)
            # Historial impide editar/eliminar la disponibilidad aun con la cita cancelada.
            _,url,error_historial=post(admin,'/disponibilidades/editar/'+disp,dict(fecha=fecha,horaInicio='09:10',horaFin='11:10'),'/disponibilidades/editar/'+disp)
            assert url.endswith('/disponibilidades') and 'role="alert"' in error_historial and 'citas asociadas' in error_historial
            _,url,error_historial=post(admin,'/disponibilidades/eliminar/'+disp,{},'/disponibilidades')
            assert url.endswith('/disponibilidades') and 'role="alert"' in error_historial and 'citas asociadas' in error_historial
            horarios=h.Inputs();horarios.feed(get(admin,'/disponibilidades/editar/'+disp));assert horarios.values['horaFin']=='10:40'
            post(admin,'/admin/citas/cancelar/'+cita_otro,{},'/admin/citas')
            assert '>Cancelada</span>' in get(otro,'/citas/mis-citas')
            filtros=dict(fechaDesde=fecha,fechaHasta=fecha,medicoId=medico,especialidad='General',estado='CANCELADA')
            consulta=urllib.parse.urlencode(filtros)
            body=get(admin,'/admin/reportes?'+consulta)
            assert re.search(r'<dt>Total</dt>\s*<dd>2</dd>',body)
            assert re.search(r'<dt>Canceladas</dt>\s*<dd>2</dd>',body)
            assert re.search(r'<option(?=[^>]*value="CANCELADA")(?=[^>]*selected)[^>]*>',body)
            valores=h.Inputs();valores.feed(body);assert valores.values['fechaDesde']==fecha and valores.values['fechaHasta']==fecha
            enlace=html.unescape(re.search(r'href="([^"]*/admin/reportes/csv[^"]*)"',body)[1])
            assert {k:v[0] for k,v in urllib.parse.parse_qs(urllib.parse.urlsplit(enlace).query).items()}==filtros
            with admin.open(base+enlace,timeout=5) as respuesta:
                assert respuesta.headers.get_content_type()=='text/csv' and 'attachment' in respuesta.headers['Content-Disposition']
                filas_csv=list(csv.reader(io.StringIO(respuesta.read().decode('utf-8-sig'))))
            assert len(filas_csv)==3 and all(len(fila)==9 for fila in filas_csv)
            body=get(admin,'/admin/citas?'+consulta);assert 'Sin citas para mostrar' not in body
            assert 'Sin resultados' in get(admin,'/admin/reportes?especialidad=SinCoincidencia')
            # Editar credenciales de otra cuenta revoca a esa cuenta, no a ADMIN.
            def id_cuenta(correo):
                usuarios=get(admin,'/usuarios')
                fila=next(f for f in re.findall(r'<tr[^>]*>(.*?)</tr>',usuarios,re.S) if correo in f)
                return re.search(r'/usuarios/editar/(\d+)',fila)[1]
            otro_id=id_cuenta('ocupacion-06b@example.test')
            editar='/usuarios/editar/'+otro_id
            valores=h.Inputs();valores.feed(get(admin,editar))
            datos_otro={k:valores.values.get(k,'') for k in ['nombre','cedula','correo','contacto','nuevaPassword']}
            datos_otro['correo']='ocupacion-editada-06c@example.test'
            _,url,_=post(admin,editar,datos_otro,editar);assert url.endswith('/usuarios?actualizado')
            assert '/login' in h.request(otro,base+'/perfil')[1]
            get(admin,'/perfil')
            post(otro,'/login',dict(username=datos_otro['correo'],password=password),'/login')
            get(otro,'/perfil')
            _,url,_=post(admin,'/usuarios/desactivar/'+otro_id,{},'/usuarios');assert url.endswith('/usuarios?desactivado')
            assert '/login' in h.request(otro,base+'/perfil')[1]
            _,url,_=post(admin,'/medicos/desactivar/'+medico,{},'/medicos');assert url.endswith('/medicos?desactivado')
            body=get(admin,'/medicos');assert 'Inactivo' in body and '/medicos/desactivar/'+medico+'"' not in body
            body=get(admin,'/admin/citas');assert nombre_medico.replace('<','&lt;').replace('>','&gt;') in body
            assert 'Sin horarios disponibles' in get(paciente,'/citas/disponibles')
            resultado.update(altas_y_ediciones_medico_disponibilidad=True,eliminacion_sin_historial=True,historial_impide_editar_y_eliminar=True,confirmacion_admin=True,cancelacion_admin=True,cancelacion_pasada_probada_en_mvc_no_http=True,filtros_conservados=True,csv_filtrado_filas=2,csv_columnas=9,csv_completo=True,cambio_sensible_otro_usuario_revoca_solo_destino=True,desactivaciones_conservan_historial=True)
            datos=dict(nombre='Paciente ficticio',cedula='000000001',correo='paciente-06a@example.test',contacto='Contacto actualizado',nuevaPassword='')
            _,url,body=post(paciente,'/perfil',datos,'/perfil')
            assert url.endswith('/perfil') and 'Perfil actualizado correctamente.' in body
            assert 'Contacto actualizado' in get(paciente,'/perfil')
            datos['nuevaPassword']=nueva
            _,url,_=post(paciente,'/perfil',datos,'/perfil');assert url.endswith('/login?sesionRestablecida')
            assert '/login' in h.request(paciente,base+'/perfil')[1]
            _,url,_=post(paciente,'/login',dict(username='paciente-06a@example.test',password=nueva),'/login');assert url.endswith('/inicio')
            get(paciente,'/perfil')
            propio_id=id_cuenta('admin-06a@example.test')
            _,url,_=post(admin,'/usuarios/desactivar/'+propio_id,{},'/usuarios')
            assert url.endswith('/login?sesionRestablecida') and '/login' in h.request(admin,base+'/usuarios')[1]
            resultado['desactivacion_propia_admin_cierra_sesion']=True
            resultado.update(resumen_http_coincide_con_parametros=True,lectura_sin_reserva=True,ocupacion_intercalada_rechazada=True,intercalado_secuencial_no_carrera=True,estado_inicial_pendiente=True,cancelacion_y_bloque_reutilizable=True,perfil_no_sensible_conserva_sesion=True,perfil_sensible_cierra_sesion=True,nuevo_login_correcto=True,alternativa_sin_js_presente_en_html=True,dialogos_en_navegador=False)
            resultado.update(formularios_publicos_y_estados=True,registro_valido_e_invalido=True,roles_y_menu_en_html=True,destino_admin='/admin/citas',destino_usuario='/inicio',lista_vacia_y_poblada=True,bootstrap_local_accesible=True,csp_y_csrf_conservados=True,base_exclusiva_memoria=True,pid_java=proceso.pid)
        finally:
            if proceso.poll() is None:proceso.terminate();proceso.wait(timeout=15)
    texto_log=(target/(args.prefijo_evidencia+'.log')).read_text(encoding='utf-8',errors='replace')
    assert password not in texto_log and nueva not in texto_log
    resultado['jvm_propia_detenida']=True
    (target/(args.prefijo_evidencia+'.json')).write_text(json.dumps(resultado,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('HTTP integral: acceso, paciente, administracion, historial, filtros/CSV y sesiones correctos. No es evidencia visual ni de dialogos.')

if __name__=='__main__':main()
