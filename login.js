// Generate floating ember particles in the art panel
const emberContainer = document.getElementById('embers');
const EMBER_COUNT = 26;

for (let i = 0; i < EMBER_COUNT; i++) {
  const e = document.createElement('div');
  e.className = 'ember';
  const left = Math.random() * 100;
  const size = 2 + Math.random() * 3;
  const duration = 6 + Math.random() * 8;
  const delay = Math.random() * 10;
  const drift = (Math.random() * 60 - 30) + 'px';
  e.style.left = left + '%';
  e.style.width = size + 'px';
  e.style.height = size + 'px';
  e.style.animationDuration = duration + 's';
  e.style.animationDelay = delay + 's';
  e.style.setProperty('--drift', drift);
  emberContainer.appendChild(e);
}

// Form validation
const form = document.getElementById('loginForm');
const emailField = document.getElementById('emailField');
const passwordField = document.getElementById('passwordField');
const emailInput = document.getElementById('email');
const pwInput = document.getElementById('password');

function validEmail(v) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
}

form.addEventListener('submit', (e) => {
  e.preventDefault();
  let valid = true;

  if (!validEmail(emailInput.value.trim())) {
    emailField.classList.add('has-error');
    valid = false;
  } else {
    emailField.classList.remove('has-error');
  }

  if (pwInput.value.length < 4) {
    passwordField.classList.add('has-error');
    valid = false;
  } else {
    passwordField.classList.remove('has-error');
  }

  if (valid) {
    // Front-end only demo: no real auth wired up.
    alert('Front-end only — hook this up to your auth API.');
  }
});

[emailInput, pwInput].forEach((inp) => {
  inp.addEventListener('input', () => inp.closest('.field').classList.remove('has-error'));
});