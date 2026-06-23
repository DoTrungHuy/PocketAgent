const labels = {
  chat: "简化 AgentPad UI",
  tasks: "Review before action",
  settings: "Model connection",
};

const navButtons = [...document.querySelectorAll("[data-view]")];
const views = [...document.querySelectorAll(".view")];
const title = document.querySelector("#title");
const form = document.querySelector("#composer");
const prompt = document.querySelector("#prompt");
const messages = document.querySelector("#messages");

function setView(view) {
  views.forEach((item) => item.classList.toggle("active", item.id === `${view}-view`));
  navButtons.forEach((button) => button.classList.toggle("active", button.dataset.view === view));
  title.textContent = labels[view] || labels.chat;
}

function addBubble(text, role) {
  const article = document.createElement("article");
  article.className = `bubble ${role}`;
  const small = document.createElement("small");
  small.textContent = role === "user" ? "You" : "AgentPad";
  const p = document.createElement("p");
  p.textContent = text;
  article.append(small, p);
  messages.append(article);
  messages.scrollTop = messages.scrollHeight;
}

navButtons.forEach((button) => {
  button.addEventListener("click", () => setView(button.dataset.view));
});

form.addEventListener("submit", (event) => {
  event.preventDefault();
  const value = prompt.value.trim();
  if (!value) return;
  addBubble(value, "user");
  prompt.value = "";
  setTimeout(() => {
    addBubble("Plan created.", "agent");
  }, 280);
});
