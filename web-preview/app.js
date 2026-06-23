const labels = {
  chat: "New document search",
  settings: "Model and privacy",
};

const navButtons = [...document.querySelectorAll("[data-view]")];
const views = [...document.querySelectorAll(".view")];
const subtitle = document.querySelector("#subtitle");
const form = document.querySelector("#composer");
const prompt = document.querySelector("#prompt");
const messages = document.querySelector("#messages");
const sheet = document.querySelector("#sheet");
const scopeCount = document.querySelector("#scope-count");
const sidebar = document.querySelector("#sidebar");
let grants = 0;

function setView(view) {
  views.forEach((item) => item.classList.toggle("active", item.id === `${view}-view`));
  navButtons.forEach((button) => button.classList.toggle("active", button.dataset.view === view));
  subtitle.textContent = labels[view] || labels.chat;
}

function clearEmpty() {
  const empty = messages.querySelector(".empty-state");
  if (empty) empty.remove();
}

function addBubble(text, role) {
  clearEmpty();
  const article = document.createElement("article");
  article.className = `bubble ${role}`;
  const small = document.createElement("small");
  small.textContent = role === "user" ? "You" : "PocketAgent";
  const p = document.createElement("p");
  p.textContent = text;
  article.append(small, p);
  messages.append(article);
  messages.scrollTop = messages.scrollHeight;
}

function addSearchCard(stage, text, withResults = false) {
  clearEmpty();
  const card = document.createElement("article");
  card.className = "search-card";
  card.innerHTML = `<div class="search-head"><span>⌕</span><div><strong>${stage}</strong><p>${text}</p></div></div>`;
  if (withResults) {
    card.insertAdjacentHTML("beforeend", `
      <div class="result"><strong>invoice-may.md</strong><small>content mentions reimbursement amount</small><p>... reimbursement amount 1280 and project travel budget ...</p></div>
      <div class="result"><strong>finance-notes.pdf</strong><small>content mentions amount</small><p>... team expense record and approval note ...</p></div>
    `);
  }
  messages.append(card);
  messages.scrollTop = messages.scrollHeight;
}

function openSheet() {
  sheet.classList.add("open");
  sheet.setAttribute("aria-hidden", "false");
}

function closeSheet() {
  sheet.classList.remove("open");
  sheet.setAttribute("aria-hidden", "true");
}

function runSearch(query) {
  addBubble(query, "user");
  addSearchCard("Indexing documents", "Reading authorized recent files.");
  setTimeout(() => addSearchCard("Understanding matches", "Ranking the best candidates.", true), 350);
  setTimeout(() => {
    addBubble("The strongest match is invoice-may.md because it explicitly mentions the reimbursement amount. finance-notes.pdf is a weaker backup candidate. In the APK, these results come from Android-authorized files and folders.", "agent");
  }, 720);
}

navButtons.forEach((button) => button.addEventListener("click", () => setView(button.dataset.view)));
document.querySelector("#menu").addEventListener("click", () => sidebar.classList.toggle("open"));
document.querySelectorAll(".thread, .nav button, #new-chat, #top-new").forEach((item) => {
  item.addEventListener("click", () => sidebar.classList.remove("open"));
});
document.querySelector("#scope").addEventListener("click", openSheet);
document.querySelector("#find").addEventListener("click", () => {
  const value = prompt.value.trim() || "Find the document that mentions reimbursement amount";
  if (!grants) {
    openSheet();
    addSearchCard("Waiting for search range", "PocketAgent needs document authorization first.");
  } else {
    prompt.value = "";
    runSearch(value);
  }
});
document.querySelector("#close-sheet").addEventListener("click", closeSheet);
document.querySelector("#grant-folder").addEventListener("click", () => {
  grants += 1;
  scopeCount.textContent = grants;
  closeSheet();
  addSearchCard("Searching recent documents", "Folder authorized. Searching recent files first.");
});
document.querySelector("#grant-files").addEventListener("click", () => {
  grants += 1;
  scopeCount.textContent = grants;
  closeSheet();
  addSearchCard("Searching selected files", "Files authorized for this document search.");
});
document.querySelector("#search-authorized").addEventListener("click", () => {
  closeSheet();
  runSearch(prompt.value.trim() || "Find the document that mentions reimbursement amount");
});
form.addEventListener("submit", (event) => {
  event.preventDefault();
  const value = prompt.value.trim();
  if (!value) return;
  prompt.value = "";
  if (/find|search|document|file|文档|文件|找|搜/.test(value.toLowerCase()) && !grants) {
    addBubble(value, "user");
    addSearchCard("Waiting for search range", "PocketAgent needs authorization before it can search phone documents.");
    openSheet();
  } else if (/find|search|document|file|文档|文件|找|搜/.test(value.toLowerCase())) {
    runSearch(value);
  } else {
    addBubble(value, "user");
    setTimeout(() => addBubble("I can help directly, or ask for a document search range when phone files are needed.", "agent"), 250);
  }
});
