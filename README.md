# DeporTV 0.1.0

App para Android TV (Xiaomi Mi Box 4K). Lee la API de Xtream Codes de tu IPTV y te enseña el deporte de los próximos días, ordenado por relevancia (primero fútbol, luego F1 y MotoGP).

## Instalar en la Mi Box
1. En la Mi Box instala **Downloader** (de AFTVnews) o **Send Files to TV** desde Google Play.
2. Ve a Ajustes → Preferencias del dispositivo → Seguridad y restricciones → **Fuentes desconocidas** y activa la app del paso 1.
3. Pasa `DeporTV-0.1.0.apk` a la Mi Box e instálala. Puedes hacerlo con Send Files to TV desde el móvil, con un pendrive o descargándola con Downloader si la subes a GitHub o Drive.
4. Abre DeporTV y mete el servidor (con el puerto), el usuario y la contraseña, los mismos que en TiviMate.

La primera actualización tarda un poco, porque descarga la guía completa. Después queda guardada y se actualiza sola cada 3 horas al abrir la app.

## Uso
- **Portada**: el evento más importante en grande y debajo varias filas: En directo, Fútbol hoy, Motor, Mañana, Otros deportes y Aplicaciones.
- **Programación**: parrilla de hoy y los 3 días siguientes, con filtros por deporte.
- **Reproductor**: ▲▼ cambia entre los canales que emiten el evento (FHD, 4K, otro operador). OK muestra la información. Si un canal falla, la app prueba primero en HLS y después salta sola al siguiente canal.
- **Ajustes**:
  - Equipos favoritos: salen siempre arriba.
  - Reproductor externo (VLC o MX Player).
  - Formato TS o HLS.
  - Categorías de canales que se analizan. Por defecto se detectan solas.

## Modo launcher (opcional)
1. Ajustes de DeporTV → Modo launcher → Activado.
2. Para que el botón Home abra DeporTV hay que desactivar el launcher de Google por ADB, desde un PC en la misma red:
   - Activa las Opciones de desarrollador en la Mi Box: pulsa 7 veces sobre "Compilación" y luego activa "Depuración USB".
   - Ejecuta:
     ```
     adb connect IP_DE_LA_MIBOX:5555
     adb shell pm disable-user --user 0 com.google.android.tvlauncher
     ```
3. Para volver al launcher original:
   ```
   adb shell pm enable com.google.android.tvlauncher
   ```

Los Ajustes de Android siguen accesibles desde Ajustes de DeporTV → "Ajustes de Android".

## Actualizaciones
Desde la 0.2.0, DeporTV mira al abrirse si hay una release más nueva en este repositorio. Si la hay, te lo avisa, descarga el APK y lo instala encima, así que no pierdes los ajustes. La primera vez Android te pedirá que permitas a DeporTV instalar apps. También puedes buscar actualizaciones a mano desde Ajustes.

Para publicar una versión nueva:
1. Sube `versionCode` y `versionName` en `app/build.gradle.kts`.
2. Sube un tag `vX.Y.Z`. La Action compila el APK y crea la release sola.

La clave de firma no está en el repositorio. La Action la lee de los secrets.

## Compilar
Necesitas el JDK 17 o superior y el Android SDK 35. Ejecuta `gradle assembleRelease`. Los tests están en `gradle testDebugUnitTest` (parser del EPG y motor de relevancia).
