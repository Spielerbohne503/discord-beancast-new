/**
 * Hell oder dunkel — noch vor dem ersten Strich.
 *
 * **Das hier ist kein ES-Modul**, und das ist Absicht: Ein `<script type="module">` läuft
 * verzögert, und bis dahin hätte der Browser die Seite längst hell gezeichnet. Es blitzt
 * dann bei jedem Laden einmal weiß auf. Ein gewöhnliches Skript im `<head>` läuft, bevor
 * irgendetwas zu sehen ist.
 *
 * Die Wahrheit über die gewählte Fassung steht in IndexedDB; die wird aber erst
 * asynchron gelesen. Der `localStorage` ist hier nur ein Vorgriff — eine Kopie, keine
 * zweite Wahrheit. `src/ui/app.js` schreibt sie nach jedem Laden fort.
 */
(function () {
  var fassung = "hell";
  try {
    fassung = localStorage.getItem("petodo.fassung") || "hell";
  } catch (fehler) {
    // Privates Fenster oder gesperrter Speicher: Dann eben hell.
  }
  document.documentElement.dataset.fassung = fassung === "dunkel" ? "dunkel" : "hell";
})();
