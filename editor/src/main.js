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
import { direction, locale, translate } from "./i18n.js";
import "./style.css";

const darkQuery = window.matchMedia("(prefers-color-scheme: dark)");
const themeCompartment = new Compartment();
const languageCompartment = new Compartment();
const languageExtensions = {
  html: html(), htm: html(), css: css(), js: javascript(), mjs: javascript(),
  json: json(), xml: xml(), md: markdown(), markdown: markdown()
};
const messages = {
  fa: { open:"بازکردن", save:"ذخیره", saveAs:"ذخیره با نام", fileManager:"مدیریت فایل‌ها", noFolder:"پوشه‌ای انتخاب نشده",
    chooseFolder:"انتخاب پوشه", newFile:"فایل جدید", newFolder:"پوشه جدید", openTabs:"تب‌های باز",
    folderContents:"محتویات پوشه", up:"بازگشت", root:"ریشه", empty:"موردی وجود ندارد", saved:"ذخیره شد", unsaved:"ذخیره نشده",
    confirmClose:"این فایل تغییر کرده است. قبل از بستن ذخیره شود؟", name:"نام فایل", error:"خطا",
    folderPrompt:"نام پوشه", filePrompt:"نام فایل جدید", deleteConfirm:"این مورد حذف شود؟",
    renamePrompt:"نام جدید", folderRequired:"ابتدا یک پوشه انتخاب کن.", ready:"آماده", preview:"پیش‌نمایش", editor:"ویرایشگر", previewUnsupported:"پیش‌نمایش فقط برای فایل‌های HTML فعال است." },
  en: { open:"Open", save:"Save", saveAs:"Save as", fileManager:"File manager", noFolder:"No folder selected",
    chooseFolder:"Choose folder", newFile:"New file", newFolder:"New folder", openTabs:"Open tabs",
    folderContents:"Folder contents", up:"Back", root:"Root", empty:"Nothing here", saved:"Saved", unsaved:"Unsaved",
    confirmClose:"This file has changes. Save before closing?", name:"File name", error:"Error",
    folderPrompt:"Folder name", filePrompt:"New file name", deleteConfirm:"Delete this item?",
    renamePrompt:"New name", folderRequired:"Choose a folder first.", ready:"Ready", preview:"Preview", editor:"Editor", previewUnsupported:"Preview is available for HTML files only." }
};
const t = key => messages[locale]?.[key] || messages.en[key] || key;
document.documentElement.lang = locale;
document.documentElement.dir = direction;
document.querySelectorAll("[data-i18n]").forEach(el => el.textContent = t(el.dataset.i18n));

let tabs = [{ id: 1, name: locale === "fa" ? "بدون‌نام.html" : "Untitled.html", text: "", uri: null, dirty: false }];
let activeId = 1;
let nextId = 2;
let folderItems = [];
let currentFolderPath = [];
let pendingSaveTabId = null;
let pendingCloseId = null;
const activeTab = () => tabs.find(tab => tab.id === activeId);
const extensionFor = name => (name.split(".").pop() || "html").toLowerCase();
const languageFor = name => ({htm:"HTML",html:"HTML",css:"CSS",js:"JavaScript",mjs:"JavaScript",json:"JSON",xml:"XML",md:"Markdown",markdown:"Markdown"}[extensionFor(name)] || "HTML");
const editorElement = document.getElementById("editor");

const editor = new EditorView({
  parent: editorElement,
  state: EditorState.create({ doc: "", extensions: [
    basicSetup, lineNumbers(), highlightActiveLine(), highlightActiveLineGutter(),
    languageCompartment.of(html()), themeCompartment.of(darkQuery.matches ? oneDark : []),
    EditorView.lineWrapping,
    EditorView.updateListener.of(update => {
      if (update.docChanged) {
        const tab = activeTab();
        if (tab) { tab.text = update.state.doc.toString(); tab.dirty = true; renderTabs(); updateHeader(); schedulePreview(); }
      }
      if (update.selectionSet || update.docChanged) updateCursorStatus(update.view);
    }),
    EditorView.theme({
      "&": { height:"100%", fontSize:"14px" },
      ".cm-scroller": { fontFamily:"ui-monospace, SFMono-Regular, Menlo, monospace", lineHeight:"1.65" },
      ".cm-content": { padding:"18px 0", caretColor:"var(--accent)" },
      ".cm-gutters": { border:"none", backgroundColor:"transparent" },
      ".cm-activeLineGutter": { backgroundColor:"var(--active-line)" }
    })
  ] })
});

function updateCursorStatus(view) {
  const pos = view.state.selection.main.head;
  const line = view.state.doc.lineAt(pos);
  document.getElementById("cursor-status").textContent = `Ln ${line.number}, Col ${pos-line.from+1}`;
}
function updateHeader() {
  const tab = activeTab();
  document.getElementById("active-file").textContent = (tab?.dirty ? "● " : "") + (tab?.name || "");
  const lang = languageFor(tab?.name || "html");
  document.getElementById("language-label").textContent = lang;
  document.getElementById("status-language").textContent = lang;
  document.getElementById("status-ready").textContent = tab?.dirty ? t("unsaved") : t("ready");
}
function renderTabs() {
  const host = document.getElementById("tabs");
  host.replaceChildren();
  tabs.forEach(tab => {
    const item = document.createElement("div");
    item.className = "tab" + (tab.id === activeId ? " active" : "");
    const label = document.createElement("button");
    label.className = "tab-label";
    label.textContent = (tab.dirty ? "● " : "") + tab.name;
    label.onclick = () => switchTab(tab.id);
    const close = document.createElement("button");
    close.className = "tab-close";
    close.textContent = "×";
    close.setAttribute("aria-label", "Close " + tab.name);
    close.onclick = () => closeTab(tab.id);
    item.append(label, close); host.append(item);
  });
  renderOpenFiles();
}
function switchTab(id) {
  const tab = tabs.find(item => item.id === id); if (!tab) return;
  if (activeTab()) activeTab().text = editor.state.doc.toString();
  activeId = id;
  editor.setState(EditorState.create({ doc:tab.text, extensions:[
    basicSetup, lineNumbers(), highlightActiveLine(), highlightActiveLineGutter(),
    languageCompartment.of(languageExtensions[extensionFor(tab.name)] || html()),
    themeCompartment.of(darkQuery.matches ? oneDark : []), EditorView.lineWrapping,
    EditorView.updateListener.of(update => {
      if (update.docChanged) {
        const current = activeTab();
        if (current) { current.text = update.state.doc.toString(); current.dirty = true; renderTabs(); updateHeader(); schedulePreview(); }
      }
      if (update.selectionSet || update.docChanged) updateCursorStatus(update.view);
    }),
    EditorView.theme({"&":{height:"100%",fontSize:"14px"},".cm-scroller":{fontFamily:"ui-monospace, SFMono-Regular, Menlo, monospace",lineHeight:"1.65"},".cm-content":{padding:"18px 0",caretColor:"var(--accent)"},".cm-gutters":{border:"none",backgroundColor:"transparent"},".cm-activeLineGutter":{backgroundColor:"var(--active-line)"}})
  ]}));
  renderTabs(); updateHeader(); schedulePreview(); editor.focus(); updateCursorStatus(editor);
}
function openTab(data) {
  const existing = data.uri ? tabs.find(tab => tab.uri === data.uri) : null;
  if (existing) { switchTab(existing.id); return; }
  const tab = { id:nextId++, name:data.name || "Untitled.html", text:data.text || "", uri:data.uri || null, projectPath:Array.isArray(data.projectPath) ? data.projectPath : null, dirty:false };
  tabs.push(tab); switchTab(tab.id);
}
function saveActive(asNew = false, tabId = activeId) {
  const tab = tabs.find(item => item.id === tabId); if (!tab) return;
  if (tab.id === activeId) tab.text = editor.state.doc.toString();
  pendingSaveTabId = tab.id;
  if (tab.uri && !asNew) NativeBridge.saveFile(tab.uri, tab.text);
  else NativeBridge.saveAs(tab.text, tab.name);
}
function finishClose(id) {
  tabs = tabs.filter(item => item.id !== id);
  if (!tabs.length) tabs.push({id:nextId++,name:locale==="fa"?"بدون‌نام.html":"Untitled.html",text:"",uri:null,dirty:false});
  if (activeId === id) activeId = tabs[0].id;
  switchTab(activeId);
}
function closeTab(id) {
  const tab = tabs.find(item => item.id === id); if (!tab) return;
  if (tab.dirty) {
    if (window.confirm(t("confirmClose"))) {
      pendingCloseId = id;
      saveActive(false, id);
      return;
    }
  }
  finishClose(id);
}
function openDrawer() {
  document.getElementById("file-drawer").classList.add("visible");
  document.getElementById("drawer-scrim").classList.add("visible");
  document.getElementById("file-drawer").setAttribute("aria-hidden","false");
  renderOpenFiles();
  currentFolderPath = [];
  NativeBridge.listFolder(JSON.stringify(currentFolderPath));
}
function closeDrawer() {
  document.getElementById("file-drawer").classList.remove("visible");
  document.getElementById("drawer-scrim").classList.remove("visible");
  document.getElementById("file-drawer").setAttribute("aria-hidden","true");
}
function renderOpenFiles() {
  const host = document.getElementById("open-file-list"); host.replaceChildren();
  tabs.forEach(tab => {
    const row = document.createElement("div"); row.className = "file-row";
    const open = document.createElement("button"); open.className = "file-open";
    open.textContent = (tab.dirty ? "● " : "▤ ") + tab.name; open.onclick = () => { switchTab(tab.id); closeDrawer(); };
    const close = document.createElement("button"); close.className = "mini-action"; close.textContent = "×"; close.onclick = () => closeTab(tab.id);
    row.append(open,close); host.append(row);
  });
}
function renderFolderItems() {
  const host = document.getElementById("folder-file-list"); host.replaceChildren();
  if (!folderItems.length) { const empty=document.createElement("p"); empty.className="empty-state"; empty.textContent=t("empty"); host.append(empty); return; }
  folderItems.forEach(item => {
    const row=document.createElement("div"); row.className="file-row";
    const open=document.createElement("button"); open.className="file-open";
    open.textContent=(item.directory?"📁 ":"📄 ")+item.name;
    open.onclick=()=>{ if(item.directory){ currentFolderPath.push(item.name); NativeBridge.listFolder(JSON.stringify(currentFolderPath)); } else NativeBridge.readFile(item.uri, JSON.stringify([...currentFolderPath, item.name])); };
    const actions=document.createElement("div"); actions.className="file-actions";
    const rename=document.createElement("button"); rename.className="mini-action"; rename.textContent="✎";
    rename.onclick=()=>{const name=window.prompt(t("renamePrompt"),item.name);if(name)NativeBridge.renameEntry(item.uri,name,JSON.stringify(currentFolderPath));};
    const del=document.createElement("button"); del.className="mini-action danger"; del.textContent="⌫";
    del.onclick=()=>{if(window.confirm(t("deleteConfirm")))NativeBridge.deleteEntry(item.uri,JSON.stringify(currentFolderPath));};
    actions.append(rename,del); row.append(open,actions); host.append(row);
  });
}
let previewVisible = false;
let previewTimer = null;
const previewFrame = document.getElementById("preview-frame");
const previewShell = document.getElementById("preview-shell");
const editorShell = document.getElementById("editor-shell");
const previewButtonLabel = document.getElementById("preview-button-label");

function renderPreview() {
  const tab = activeTab();
  if (!tab) return;
  const extension = extensionFor(tab.name);
  if (extension !== "html" && extension !== "htm") {
    const message = t("previewUnsupported");
    previewFrame.srcdoc = `<!doctype html><html><body style="font-family:system-ui;padding:24px;color:#777;text-align:center">${message}</body></html>`;
    return;
  }
  let source = tab.text || "<!doctype html><html><body></body></html>";
  if (Array.isArray(tab.projectPath) && tab.projectPath.length) {
    const basePath = tab.projectPath.slice(0, -1).map(part => encodeURIComponent(part)).join("/");
    const baseTag = `<base href="https://appassets.androidplatform.net/project/${basePath ? basePath + "/" : ""}">`;
    if (/<base\\b[^>]*>/i.test(source)) source = source.replace(/<base\\b[^>]*>/i, baseTag);
    else if (/<head\\b[^>]*>/i.test(source)) source = source.replace(/<head\\b[^>]*>/i, match => match + baseTag);
    else source = baseTag + source;
  }
  previewFrame.srcdoc = source;
}
function schedulePreview() {
  if (!previewVisible) return;
  if (previewTimer) clearTimeout(previewTimer);
  previewTimer = setTimeout(() => {
    previewTimer = null;
    renderPreview();
  }, 250);
}
function setPreviewMode(visible) {
  previewVisible = visible;
  editorShell.hidden = visible;
  previewShell.hidden = !visible;
  previewButtonLabel.textContent = visible ? t("editor") : t("preview");
  if (visible) renderPreview();
}

function applyTheme() {
  document.documentElement.dataset.theme = darkQuery.matches ? "dark" : "light";
  editor.dispatch({effects:themeCompartment.reconfigure(darkQuery.matches ? oneDark : [])});
}
darkQuery.addEventListener?.("change",applyTheme);
applyTheme(); updateHeader(); updateCursorStatus(editor); renderTabs();

document.getElementById("preview-button").onclick=()=>setPreviewMode(!previewVisible);
document.getElementById("files-button").onclick=openDrawer;
document.getElementById("close-drawer").onclick=closeDrawer;
document.getElementById("drawer-scrim").onclick=closeDrawer;
document.getElementById("open-button").onclick=()=>NativeBridge.openFile();
document.getElementById("save-button").onclick=()=>saveActive(false);
document.getElementById("saveas-button").onclick=()=>saveActive(true);
document.getElementById("choose-folder").onclick=()=>{currentFolderPath=[];NativeBridge.chooseFolder();};
document.getElementById("folder-up").onclick=()=>{if(currentFolderPath.length){currentFolderPath.pop();NativeBridge.listFolder(JSON.stringify(currentFolderPath));}};
document.getElementById("new-file").onclick=()=>{const name=window.prompt(t("filePrompt"),"new-file.html");if(name)NativeBridge.createEntry(name,false,JSON.stringify(currentFolderPath));};
document.getElementById("new-folder").onclick=()=>{const name=window.prompt(t("folderPrompt"));if(name)NativeBridge.createEntry(name,true,JSON.stringify(currentFolderPath));};

function parsePayload(payload) { try { return typeof payload==="string" ? JSON.parse(payload) : payload; } catch { return {}; } }
window.htmlEditor = Object.freeze({
  onOpenResult(payload) { const data=parsePayload(payload); if(data.ok) {openTab(data);closeDrawer();} else if(!data.cancelled) window.alert(data.error||t("error")); },
  onSaveResult(payload) {
    const data=parsePayload(payload);
    const tab=tabs.find(item=>item.id===pendingSaveTabId);
    if(data.ok && tab){
      tab.uri=data.uri||tab.uri;tab.name=data.name||tab.name;tab.dirty=false;
      updateHeader();renderTabs();document.getElementById("status-ready").textContent=t("saved");
      if(pendingCloseId===tab.id){const id=pendingCloseId;pendingCloseId=null;pendingSaveTabId=null;finishClose(id);}
    } else if(!data.cancelled) window.alert(data.error||t("error"));
    if(!data.ok){pendingCloseId=null;pendingSaveTabId=null;}
  },
  onFolderListed(payload) {
    const data=parsePayload(payload); folderItems=data.items||[];
    currentFolderPath = Array.isArray(data.path) ? data.path : currentFolderPath;
    document.getElementById("folder-label").textContent=data.ok?(data.folder||t("root")):t("noFolder");
    document.getElementById("folder-up").hidden = currentFolderPath.length === 0;
    document.getElementById("folder-path").textContent = currentFolderPath.length ? [t("root"), ...currentFolderPath].join("  /  ") : t("root");
    renderFolderItems();
  }
});
