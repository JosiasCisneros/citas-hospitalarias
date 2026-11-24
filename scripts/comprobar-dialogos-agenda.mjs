// Prueba de logica del JS real con eventos/DOM simulados. No acredita dialogo/foco nativos.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const fuente=fs.readFileSync(new URL('../Citas/src/main/resources/static/js/agenda.js',import.meta.url),'utf8');
let casos=0;
function entorno(tipo='reserva',soporte=true) {
    let activo=null;
    class Elemento {
        constructor(){this.listeners=new Map();this.isConnected=true;this.attrs=new Map();this.textContent='';const clases=new Set();this.classList={toggle(c,on){on?clases.add(c):clases.delete(c);},contains(c){return clases.has(c);}};}
        addEventListener(tipo,fn){if(!this.listeners.has(tipo))this.listeners.set(tipo,[]);this.listeners.get(tipo).push(fn);}
        emitir(tipo,extra={}){const event={defaultPrevented:false,preventDefault(){this.defaultPrevented=true;},...extra};for(const fn of this.listeners.get(tipo)||[])fn(event);return event;}
        focus(){activo=this;}
        setAttribute(k,v){this.attrs.set(k,v);}
        removeAttribute(k){this.attrs.delete(k);}
        set innerHTML(_){throw new Error('No se permite innerHTML para datos');}
    }
    const boton=new Elemento();boton.name='accion';boton.value=tipo;
    class Formulario extends Elemento {
        constructor(){super();this.valid=true;this.posts=[];this.fields={_csrf:'valor-simulado',horaInicio:'09:10'};this.dataset={medico:'Medica <img src=x onerror=alert(1)>',especialidad:'General & familiar',fecha:'25/11/2030',inicio:'09:10',fin:'09:40',titulo:'¿Desactivar esta cuenta?',mensaje:'Cuenta <img src=x> e historial conservado',accion:'Desactivar'};}
        querySelector(selector){assert.equal(selector,'button[type=submit]');return boton;}
        requestSubmit(submitter){if(!this.valid)return;const e=this.emitir('submit',{submitter});if(!e.defaultPrevented)this.posts.push({submitter,fields:{...this.fields}});}
    }
    class Dialogo extends Elemento {
        constructor(){super();this.open=false;this.volver=new Elemento();this.enviar=new Elemento();}
        showModal(){assert.equal(this.open,false);this.open=true;}
        close(){if(this.open){this.open=false;this.emitir('close');}}
        querySelector(selector){return selector==='[data-volver]'?this.volver:this.enviar;}
    }
    const form=new Formulario(),dialogo=new Dialogo(),clases=new Set(),eventos=new Map();
    const campos=Object.fromEntries(['medico','especialidad','fecha','inicio','fin'].map(c=>['revision-'+c,new Elemento()]));
    campos['titulo-accion']=new Elemento();campos['mensaje-accion']=new Elemento();
    const document={
        body:{classList:{add(c){clases.add(c);}}},
        getElementById(id){return campos[id]||((tipo==='reserva'&&id==='revisar-reserva')||(tipo==='cancelacion'&&id==='cancelar-cita')||(tipo==='accion'&&id==='confirmar-accion')?dialogo:null);},
        querySelectorAll(selector){return selector===`form[data-revisar-${tipo}]`?[form]:[];}
    };
    const window={addEventListener(tipo,fn){eventos.set(tipo,fn);}};
    vm.runInNewContext(fuente,{document,window,HTMLDialogElement:soporte?Dialogo:undefined,HTMLFormElement:Formulario});
    return {form,dialogo,boton,campos,clases,eventos,activo:()=>activo};
}
function caso(fn){fn();casos++;}
caso(()=>{
    const e=entorno();const original={...e.form.fields};
    e.form.requestSubmit(e.boton);
    assert.equal(e.dialogo.open,true);assert.equal(e.form.posts.length,0);assert.equal(e.activo(),e.dialogo.volver);
    for(const c of ['medico','especialidad','fecha','inicio','fin'])assert.equal(e.campos['revision-'+c].textContent,e.form.dataset[c]);
    e.dialogo.volver.emitir('click');assert.equal(e.form.posts.length,0);assert.equal(e.dialogo.open,false);assert.equal(e.activo(),e.boton);
    assert.deepEqual(e.form.fields,original);
});
caso(()=>{
    const e=entorno();e.form.requestSubmit(e.boton);e.dialogo.enviar.emitir('click');e.dialogo.enviar.emitir('click');
    assert.equal(e.form.posts.length,1);assert.equal(e.form.posts[0].submitter,e.boton);
    assert.deepEqual(e.form.posts[0].fields,{_csrf:'valor-simulado',horaInicio:'09:10'});
    assert.equal(e.form.emitir('submit',{submitter:e.boton}).defaultPrevented,true);
    assert.equal(e.form.attrs.get('aria-busy'),'true');
});
caso(()=>{
    const e=entorno();e.form.requestSubmit(e.boton);e.dialogo.close();
    assert.equal(e.form.posts.length,0);assert.equal(e.activo(),e.boton);
    e.form.requestSubmit(e.boton);assert.equal(e.dialogo.open,true);assert.equal(e.form.posts.length,0);
});
caso(()=>{
    const e=entorno();e.form.requestSubmit(null);e.dialogo.enviar.emitir('click');
    assert.equal(e.form.posts.length,1);assert.equal(e.form.posts[0].submitter,e.boton);
});
caso(()=>{
    const e=entorno('cancelacion');e.form.requestSubmit(e.boton);e.dialogo.volver.emitir('click');
    assert.equal(e.form.posts.length,0);assert.equal(e.activo(),e.boton);
    e.form.requestSubmit(e.boton);e.dialogo.enviar.emitir('click');assert.equal(e.form.posts.length,1);
});
caso(()=>{
    const e=entorno();e.form.requestSubmit(e.boton);e.form.valid=false;e.dialogo.enviar.emitir('click');
    assert.equal(e.form.posts.length,0);e.form.valid=true;e.form.requestSubmit(e.boton);assert.equal(e.dialogo.open,true);
});
caso(()=>{
    const e=entorno();e.form.requestSubmit(e.boton);e.dialogo.enviar.emitir('click');
    e.eventos.get('pageshow')();assert.equal(e.form.attrs.has('aria-busy'),false);
    e.form.requestSubmit(e.boton);assert.equal(e.dialogo.open,true);
});
caso(()=>{
    const e=entorno('reserva',false);assert.equal(e.clases.has('dialogos-agenda'),false);
    e.form.requestSubmit(e.boton);assert.equal(e.form.posts.length,1);assert.equal(e.dialogo.open,false);
});
caso(()=>{
    const e=entorno('accion');e.form.requestSubmit(e.boton);
    assert.equal(e.form.posts.length,0);assert.equal(e.campos['titulo-accion'].textContent,e.form.dataset.titulo);
    assert.equal(e.campos['mensaje-accion'].textContent,e.form.dataset.mensaje);
    assert.equal(e.dialogo.enviar.textContent,'Desactivar');
    e.dialogo.volver.emitir('click');assert.equal(e.form.posts.length,0);assert.equal(e.activo(),e.boton);
});
caso(()=>{
    const e=entorno('accion');e.form.dataset.titulo='¿Quieres cancelar esta cita?';
    e.form.dataset.mensaje='La cita quedará cancelada y se conservará su historial.';
    e.form.dataset.accion='Cancelar cita';e.form.requestSubmit(e.boton);
    assert.equal(e.dialogo.enviar.textContent,'Cancelar cita');
    assert.equal(e.campos['mensaje-accion'].textContent,e.form.dataset.mensaje);
    e.dialogo.enviar.emitir('click');e.dialogo.enviar.emitir('click');
    assert.equal(e.form.posts.length,1);assert.equal(e.form.posts[0].submitter,e.boton);
    assert.deepEqual(e.form.posts[0].fields,e.form.fields);
});
caso(()=>{
    const e=entorno('accion',false);e.form.requestSubmit(e.boton);
    assert.equal(e.form.posts.length,1);assert.equal(e.clases.has('dialogos-agenda'),false);
});
caso(()=>{
    const e=entorno('accion');
    // Alternar acciones en el mismo diálogo debe restablecer el estilo en ambos sentidos.
    for(const primario of [false,true,false,true]) {
        e.form.dataset.estilo=primario?'primario':'';
        e.form.dataset.titulo=primario?'¿Quieres confirmar esta cita?':'¿Quieres cancelar esta cita?';
        e.form.dataset.mensaje=primario?'La cita pasará al estado Confirmada.':'La cita quedará cancelada y se conservará su historial.';
        e.form.dataset.accion=primario?'Confirmar cita':'Cancelar cita';
        e.form.requestSubmit(e.boton);
        assert.equal(e.dialogo.enviar.classList.contains('boton'),primario);
        assert.equal(e.dialogo.enviar.classList.contains('boton-peligro'),!primario);
        assert.equal(e.campos['titulo-accion'].textContent,e.form.dataset.titulo);
        assert.equal(e.campos['mensaje-accion'].textContent,e.form.dataset.mensaje);
        assert.equal(e.dialogo.enviar.textContent,e.form.dataset.accion);
        e.dialogo.volver.emitir('click');
        assert.equal(e.form.posts.length,0);assert.equal(e.activo(),e.boton);
    }
    e.form.requestSubmit(e.boton);e.dialogo.close(); // Cierre nativo simulado, no prueba Escape real.
    assert.equal(e.form.posts.length,0);
    e.form.requestSubmit(e.boton);e.dialogo.enviar.emitir('click');e.dialogo.enviar.emitir('click');
    assert.equal(e.form.posts.length,1);assert.equal(e.form.posts[0].submitter,e.boton);
    assert.deepEqual(e.form.posts[0].fields,e.form.fields);
});
console.log(JSON.stringify({resultado:'paso',casos,dom_eventos_simulados:true,navegador_real:false,foco_y_escape_nativos:false}));
