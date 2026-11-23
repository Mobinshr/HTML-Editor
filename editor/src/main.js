import { EditorView, lineNumbers, highlightActiveLine, highlightActiveLineGutter } from "@codemirror/view";
import { EditorState, Compartment } from "@codemirror/state";
import { basicSetup } from "codemirror";
import { html } from "@codemirror/lang-html";
import { css } from "@codemirror/lang-css";
import { javascript } from "@codemirror/lang-javascript";
import { json } from "@codemirror/lang-json";
import { xml } from "@codemirror/lang-xml";
import { markdown } from "@codemirror/lang-markdown";
import { oneDark } from "@codemirror/theme-one-dark";
import { direction, translate } from "./i18n.js";
import "./style.css";

const darkQuery = window.matchMedia("(prefers-color-scheme: dark)");
const themeCompartment = new Compartment();
const languageExtensions = {
  html: html(),
  htm: html(),
  css: css(),
  js: javascript(),
  mjs: javascript(),
  json: json(),
  xml: xml(),
  md: markdown(),
  markdown: markdown()
};

document.documentElement.lang = direction === "rtl" ? "fa" : "en";
document.documentElement.dir = direction;
document.getElementById("active-file").textContent = translate("untitled");
document.getElementById("status-ready").textContent = translate("ready");

function currentLanguage() {
  const extension = (document.getElementById("active-file").textContent.split(".").pop() || "html").toLowerCase();
  return languageExtensions[extension] || html();
}

const editor = new EditorView({
  parent: document.getElementById("editor"),
  state: EditorState.create({
    doc: "",
    extensions: [
      basicSetup,
      lineNumbers(),
      highlightActiveLine(),
      highlightActiveLineGutter(),
      currentLanguage(),
      themeCompartment.of(darkQuery.matches ? oneDark : []),
      EditorView.lineWrapping,
      EditorView.updateListener.of((update) => {
        if (update.selectionSet || update.docChanged) updateCursorStatus(update.view);
      }),
      EditorView.theme({
        "&": { height: "100%", fontSize: "14px" },
        ".cm-scroller": { fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace", lineHeight: "1.65" },
        ".cm-content": { padding: "18px 0", caretColor: "var(--accent)" },
        ".cm-gutters": { border: "none", backgroundColor: "transparent" },
        ".cm-activeLineGutter": { backgroundColor: "var(--active-line)" }
      })
    ]
  })
});

function updateCursorStatus(view) {
  const position = view.state.selection.main.head;
  const line = view.state.doc.lineAt(position);
  document.getElementById("cursor-status").textContent = `Ln ${line.number}, Col ${position - line.from + 1}`;
}

function applyColorScheme() {
  document.documentElement.dataset.theme = darkQuery.matches ? "dark" : "light";
  editor.dispatch({
    effects: themeCompartment.reconfigure(darkQuery.matches ? oneDark : [])
  });
}

darkQuery.addEventListener?.("change", applyColorScheme);
applyColorScheme();
updateCursorStatus(editor);

window.htmlEditor = Object.freeze({
  getText: () => editor.state.doc.toString(),
  focus: () => editor.focus()
});
