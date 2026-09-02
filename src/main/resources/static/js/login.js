const wrapper = document.querySelector(".wrapper");
const loginLink = document.querySelector(".login-link");
const registerLink = document.querySelector(".register-link");
const btnPopup = document.querySelector(".btn-login");
const iconClose = document.querySelector(".icon-close");
const registerForm = document.querySelector("#registerForm");

registerLink.addEventListener("click", (e) => {
  e.preventDefault();
  wrapper.classList.add("active");
});

loginLink.addEventListener("click", (e) => {
  e.preventDefault();
  wrapper.classList.remove("active");
});

btnPopup.addEventListener("click", () => {
  wrapper.classList.add("active-popup");
});

iconClose.addEventListener("click", () => {
  wrapper.classList.remove("active-popup");
  wrapper.classList.remove("active");
});

registerForm.addEventListener("submit", (e) => {
  e.preventDefault();
});
