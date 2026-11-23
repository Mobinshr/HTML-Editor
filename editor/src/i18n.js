const preferred = (navigator.language || "en").toLowerCase();
export const locale = preferred.startsWith("fa") ? "fa" : "en";
export const direction = locale === "fa" ? "rtl" : "ltr";

const messages = {
  en: { ready: "Ready", untitled: "Untitled.html" },
  fa: { ready: "آماده", untitled: "بدون‌نام.html" }
};

export function translate(key) {
  return messages[locale][key] || messages.en[key] || key;
}
