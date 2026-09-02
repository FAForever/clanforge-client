(function(){
  'use strict';

  /* ---- mobile nav toggle ---- */
  var menuToggle = document.getElementById('menuToggle');
  var primaryNav = document.getElementById('primaryNav');

  function closeNav(){
    primaryNav.classList.remove('is-open');
    menuToggle.classList.remove('is-open');
    menuToggle.setAttribute('aria-expanded','false');
  }
  menuToggle.addEventListener('click', function(){
    var isOpen = primaryNav.classList.toggle('is-open');
    menuToggle.classList.toggle('is-open', isOpen);
    menuToggle.setAttribute('aria-expanded', String(isOpen));
  });
  primaryNav.querySelectorAll('a').forEach(function(link){
    link.addEventListener('click', function(e){
      if(link.getAttribute('href') === '#'){ e.preventDefault(); }
      closeNav();
    });
  });

  /* ---- popovers (notifications / account) ---- */
  function setupPopover(btnId, panelId){
    var btn = document.getElementById(btnId);
    var panel = document.getElementById(panelId);
    if(!btn || !panel) return;
    btn.addEventListener('click', function(e){
      e.stopPropagation();
      var willOpen = !panel.classList.contains('is-open');
      document.querySelectorAll('.popover.is-open').forEach(function(p){ p.classList.remove('is-open'); });
      document.querySelectorAll('.icon-btn[aria-expanded="true"]').forEach(function(b){ b.setAttribute('aria-expanded','false'); });
      if(willOpen){
        panel.classList.add('is-open');
        btn.setAttribute('aria-expanded','true');
      }
    });
  }
  setupPopover('notifBtn','notifPanel');
  setupPopover('avatarBtn','avatarPanel');

  document.addEventListener('click', function(e){
    document.querySelectorAll('.popover.is-open').forEach(function(p){
      if(!p.parentElement.contains(e.target)){
        p.classList.remove('is-open');
        var btn = p.parentElement.querySelector('.icon-btn');
        if(btn) btn.setAttribute('aria-expanded','false');
      }
    });
  });
  document.addEventListener('keydown', function(e){
    if(e.key === 'Escape'){
      document.querySelectorAll('.popover.is-open').forEach(function(p){ p.classList.remove('is-open'); });
      closeNav();
    }
  });

  /* ---- live preview ---- */
  var nameInput = document.getElementById('clanName');
  var tagInput = document.getElementById('clanTag');
  var descInput = document.getElementById('clanDesc');
  var publicToggle = document.getElementById('publicToggle');

  var previewBadgeText = document.getElementById('previewBadgeText');
  var previewBadgeImg = document.getElementById('previewBadgeImg');
  var previewName = document.getElementById('previewName');
  var previewTag = document.getElementById('previewTag');
  var previewDesc = document.getElementById('previewDesc');
  var previewStatus = document.getElementById('previewStatus');

  function updatePreview(){
    var name = nameInput.value.trim();
    var tag = tagInput.value.trim().toUpperCase();
    var desc = descInput.value.trim();

    previewName.textContent = name || 'Your Clan Name';
    previewTag.textContent = '[' + (tag || 'TAG') + ']';
    previewBadgeText.textContent = tag || 'TAG';
    previewDesc.textContent = desc || 'Your clan description will appear here.';

    var isPublic = publicToggle.checked;
    previewStatus.textContent = isPublic ? 'Public' : 'Private';
    previewStatus.classList.toggle('is-public', isPublic);
    previewStatus.classList.toggle('is-private', !isPublic);
  }

  [nameInput, descInput].forEach(function(el){
    el.addEventListener('input', updatePreview);
  });
  publicToggle.addEventListener('change', updatePreview);

  /* ---- clan tag: exactly 3 alphanumeric characters ---- */
  var tagField = document.getElementById('clanTagField');
  var tagHint = document.getElementById('clanTagHint');
  var TAG_HINT_DEFAULT = 'Exactly 3 letters or numbers. Shown in brackets, like [IVG].';
  var TAG_HINT_ERROR = 'Clan tag must be exactly 3 letters or numbers.';

  function clearTagError(){
    tagField.classList.remove('has-error');
    tagHint.textContent = TAG_HINT_DEFAULT;
    tagHint.classList.remove('is-error');
  }
  function validateTag(){
    if(tagInput.value.trim().length !== 3){
      tagField.classList.add('has-error');
      tagHint.textContent = TAG_HINT_ERROR;
      tagHint.classList.add('is-error');
      return false;
    }
    clearTagError();
    return true;
  }
  tagInput.addEventListener('input', function(){
    var cleaned = tagInput.value.replace(/[^a-zA-Z0-9]/g, '').slice(0, 3);
    if(cleaned !== tagInput.value){ tagInput.value = cleaned; }
    clearTagError();
    updatePreview();
  });
  tagInput.addEventListener('blur', validateTag);

  updatePreview();

  /* ---- clan image upload ---- */
  var imageInput = document.getElementById('clanImageInput');
  var imagePreviewImg = document.getElementById('imagePreviewImg');
  var imagePreviewText = document.getElementById('imagePreviewText');
  var removeImageBtn = document.getElementById('removeImageBtn');
  var uploadedImageDataUrl = null;

  function applyImagePreview(){
    var hasImage = !!uploadedImageDataUrl;
    imagePreviewImg.hidden = !hasImage;
    imagePreviewText.hidden = hasImage;
    previewBadgeImg.hidden = !hasImage;
    previewBadgeText.hidden = hasImage;
    removeImageBtn.hidden = !hasImage;
    if(hasImage){
      imagePreviewImg.src = uploadedImageDataUrl;
      previewBadgeImg.src = uploadedImageDataUrl;
    }
  }

  imageInput.addEventListener('change', function(){
    var file = imageInput.files && imageInput.files[0];
    if(!file) return;
    var reader = new FileReader();
    reader.onload = function(e){
      uploadedImageDataUrl = e.target.result;
      applyImagePreview();
    };
    reader.readAsDataURL(file);
  });

  removeImageBtn.addEventListener('click', function(){
    uploadedImageDataUrl = null;
    imageInput.value = '';
    applyImagePreview();
  });

  /* ---- create clan submission ---- */
  var createBtn = document.getElementById('createClanBtn');
  var formView = document.getElementById('createFormView');
  var successView = document.getElementById('createSuccessView');
  var successText = document.getElementById('successText');

  createBtn.addEventListener('click', function(){
    var name = nameInput.value.trim();

    if(!name){ nameInput.focus(); return; }
    if(!validateTag()){ tagInput.focus(); return; }

    var tag = tagInput.value.trim().toUpperCase();
    successText.textContent = '"' + name + ' [' + tag + ']" is live. Head to My Clan to start building your roster.';
    formView.hidden = true;
    successView.hidden = false;
    successView.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });

})();
