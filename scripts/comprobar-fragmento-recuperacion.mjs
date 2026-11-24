// Comprobación de lógica JS sin dependencias. No sustituye revisión visual del navegador.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';
const source = fs.readFileSync(fileURLToPath(new URL('../Citas/src/main/resources/static/js/recuperacion.js', import.meta.url)), 'utf8');
for (const [fragmento, valido] of [['#token=' + 'A'.repeat(43), true], ['#token=invalido', false], ['', false]]) {
    const campos = {
        'token-recuperacion': {value: ''},
        'restablecer': {disabled: true},
        'estado-enlace': {textContent: ''}
    };
    let direccionLimpia;
    const contexto = {
        URLSearchParams,
        window: {
            location: {hash: fragmento, pathname: '/cambiar-password'},
            history: {replaceState: (state, title, url) => { direccionLimpia = url; }}
        },
        document: {getElementById: id => campos[id]}
    };
    vm.runInNewContext(source, contexto);
    assert.equal(direccionLimpia, '/cambiar-password');
    assert.equal(campos.restablecer.disabled, !valido);
    assert.equal(campos['token-recuperacion'].value.length, valido ? 43 : 0);
}
console.log(JSON.stringify({resultado: 'paso', casos_fragmento: 3, navegador_visual: false}));
