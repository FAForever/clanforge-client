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

  /* ---- save changes ---- */
  var saveBtn = document.getElementById('saveBtn');
  var saveConfirmation = document.getElementById('saveConfirmation');
  var saveTimeout = null;

  saveBtn.addEventListener('click', function(){
    if(!validateTag()){ tagInput.focus(); return; }
    saveConfirmation.classList.add('is-visible');
    window.clearTimeout(saveTimeout);
    saveTimeout = window.setTimeout(function(){
      saveConfirmation.classList.remove('is-visible');
    }, 2400);
  });

  /* ---- message of the day ---- */
  var motdSaveBtn = document.getElementById('motdSaveBtn');
  var motdConfirmation = document.getElementById('motdConfirmation');
  var motdTimeout = null;

  motdSaveBtn.addEventListener('click', function(){
    motdConfirmation.classList.add('is-visible');
    window.clearTimeout(motdTimeout);
    motdTimeout = window.setTimeout(function(){
      motdConfirmation.classList.remove('is-visible');
    }, 2400);
  });

  /* ---- clan events ---- */
  var eventList = document.getElementById('eventList');
  var eventsEmpty = document.getElementById('eventsEmpty');
  var eventTitleInput = document.getElementById('eventTitleInput');
  var eventDateInput = document.getElementById('eventDateInput');
  var eventTimeInput = document.getElementById('eventTimeInput');
  var addEventBtn = document.getElementById('addEventBtn');

  var MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
  var WEEKDAYS = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];

  function updateEventsUI(){
    var count = eventList.querySelectorAll('.event-row').length;
    eventsEmpty.classList.toggle('is-visible', count === 0);
  }

  function formatEventDateParts(dateStr){
    var bits = dateStr.split('-').map(Number);
    var d = new Date(bits[0], bits[1] - 1, bits[2]);
    return { day: bits[2], mon: MONTHS[bits[1] - 1], weekday: WEEKDAYS[d.getDay()] };
  }
  function formatEventTime(timeStr){
    var bits = timeStr.split(':').map(Number);
    var ampm = bits[0] >= 12 ? 'PM' : 'AM';
    var hour12 = bits[0] % 12 || 12;
    var minuteStr = bits[1] < 10 ? '0' + bits[1] : String(bits[1]);
    return hour12 + ':' + minuteStr + ' ' + ampm;
  }

  eventList.addEventListener('click', function(e){
    var btn = e.target.closest('.remove-event-btn');
    if(!btn) return;
    var row = e.target.closest('.event-row');
    if(row){ row.remove(); updateEventsUI(); }
  });

  addEventBtn.addEventListener('click', function(){
    var title = eventTitleInput.value.trim();
    var dateVal = eventDateInput.value;
    var timeVal = eventTimeInput.value;

    if(!title){ eventTitleInput.focus(); return; }
    if(!dateVal){ eventDateInput.focus(); return; }
    if(!timeVal){ eventTimeInput.focus(); return; }

    var dateParts = formatEventDateParts(dateVal);
    var timeLabel = formatEventTime(timeVal) + ' UTC · ' + dateParts.weekday;

    var row = document.createElement('div');
    row.className = 'event-row';
    row.innerHTML =
      '<div class="event-date"><span class="day"></span><span class="mon"></span></div>' +
      '<div class="event-row-info"><p class="event-title"></p><p class="event-time"></p></div>' +
      '<button type="button" class="link-btn remove-event-btn">Remove</button>';
    row.querySelector('.day').textContent = dateParts.day;
    row.querySelector('.mon').textContent = dateParts.mon;
    row.querySelector('.event-title').textContent = title;
    row.querySelector('.event-time').textContent = timeLabel;

    eventList.appendChild(row);
    updateEventsUI();

    eventTitleInput.value = '';
    eventDateInput.value = '';
    eventTimeInput.value = '';
    eventTitleInput.focus();
  });

  updateEventsUI();

  /* ---- danger zone ---- */
  var transferBtn = document.getElementById('transferBtn');
  var disbandBtn = document.getElementById('disbandBtn');
  var dangerFeedback = document.getElementById('dangerFeedback');
  var dangerTimeout = null;

  function showDangerFeedback(text){
    dangerFeedback.textContent = text;
    dangerFeedback.classList.add('is-visible');
    window.clearTimeout(dangerTimeout);
    dangerTimeout = window.setTimeout(function(){
      dangerFeedback.classList.remove('is-visible');
    }, 4000);
  }

  transferBtn.addEventListener('click', function(){
    var confirmed = window.confirm('Transfer your Leader role to another officer? You will become a regular Officer.');
    if(confirmed){
      showDangerFeedback('✓ Leadership transfer started (demo only — no changes were made).');
    }
  });

  disbandBtn.addEventListener('click', function(){
    var confirmed = window.confirm('Permanently disband Iron Vanguard and remove all 42 members? This cannot be undone.');
    if(confirmed){
      showDangerFeedback('✓ Demo only — Iron Vanguard was not actually disbanded.');
    }
  });

})();
