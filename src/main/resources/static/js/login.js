// Landing page: the login panel popup, and switching between its login and
// register halves.
//
// Every element is optional. The login and register forms are rendered
// conditionally - with OIDC configured the password form is not on the page at
// all - so querySelector returns null for anything inside a hidden block.
// Without these guards the first missing element throws a TypeError and every
// later handler in this file, including the one that opens the panel, is never
// registered. The symptom is a Login button that silently does nothing.
const wrapper = document.querySelector(".wrapper");
const loginLink = document.querySelector(".login-link");
const registerLink = document.querySelector(".register-link");
const btnPopup = document.querySelector(".btn-login");
const iconClose = document.querySelector(".icon-close");
const registerForm = document.querySelector("#registerForm");

if (registerLink && wrapper) {
  registerLink.addEventListener("click", (e) => {
    e.preventDefault();
    wrapper.classList.add("active");
  });
}

if (loginLink && wrapper) {
  loginLink.addEventListener("click", (e) => {
    e.preventDefault();
    wrapper.classList.remove("active");
  });
}

if (btnPopup && wrapper) {
  btnPopup.addEventListener("click", () => {
    wrapper.classList.add("active-popup");
  });
}

if (iconClose && wrapper) {
  iconClose.addEventListener("click", () => {
    wrapper.classList.remove("active-popup");
    wrapper.classList.remove("active");
  });
}

if (registerForm) {
  registerForm.addEventListener("submit", (e) => {
    e.preventDefault();
  });
}
