# Bootstrap local

Se conserva **Bootstrap 5.3.3**, ya utilizado por citas-disponibles.html. Solo esa pantalla carga su CSS y bundle JS; no se añade globalmente ni se cambia la versión.

Copias de la distribución oficial del tag v5.3.3, obtenidas el 2026-11-19:

- [CSS minificado](https://raw.githubusercontent.com/twbs/bootstrap/v5.3.3/dist/css/bootstrap.min.css) → `Citas/src/main/resources/static/css/bootstrap.min.css`. SHA-256 `3c8f27e6009ccfd710a905e6dcf12d0ee3c6f2ac7da05b0572d3e0d12e736fc8`.
- [Bundle JS](https://raw.githubusercontent.com/twbs/bootstrap/v5.3.3/dist/js/bootstrap.bundle.min.js) → `Citas/src/main/resources/static/js/bootstrap.bundle.min.js`. SHA-256 `0833b2e9c3a26c258476c46266e6877fc75218625162e0460be9a3a098a61c6c`.
- [Licencia MIT](https://raw.githubusercontent.com/twbs/bootstrap/v5.3.3/LICENSE) → `Citas/src/main/resources/static/css/BOOTSTRAP-LICENSE.txt`. SHA-256 `8c14611ae41ac6fd543c13349f22188eb12c69b3e59105c5eca3925a8e4eca3e`.

Se conservan los avisos incluidos en los minificados y la licencia completa. Los mapas de fuentes no son necesarios para ejecutar la aplicación y no se incorporan. No se descargan fuentes, iconos u otros paquetes.
