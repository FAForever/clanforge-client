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

  /* ---- invitations: applications, sent invites, send form ---- */
  var applicationsList = document.getElementById('applicationsList');
  var sentList = document.getElementById('sentList');
  var applicationsEmpty = document.getElementById('applicationsEmpty');
  var sentEmpty = document.getElementById('sentEmpty');
  var pendingCountEl = document.getElementById('pendingCount');
  var sentCountEl = document.getElementById('sentCount');
  var joinedCountEl = document.getElementById('joinedCount');
  var inviteForm = document.getElementById('inviteForm');
  var inviteUsername = document.getElementById('inviteUsername');
  var inviteRole = document.getElementById('inviteRole');

  function initials(name){
    var clean = name.replace(/[^a-zA-Z0-9]/g, ' ').trim();
    var parts = clean.split(/\s+/).filter(Boolean);
    if(parts.length >= 2){ return (parts[0][0] + parts[1][0]).toUpperCase(); }
    return (clean.slice(0,2) || '??').toUpperCase();
  }

  function updateApplicationsUI(){
    var count = applicationsList.querySelectorAll('.invite-row').length;
    if(pendingCountEl) pendingCountEl.textContent = count;
    if(applicationsEmpty) applicationsEmpty.classList.toggle('is-visible', count === 0);
  }
  function updateSentUI(){
    var count = sentList.querySelectorAll('.invite-row').length;
    if(sentCountEl) sentCountEl.textContent = count;
    if(sentEmpty) sentEmpty.classList.toggle('is-visible', count === 0);
  }

  function removeRow(row, after){
    row.classList.add('is-removing');
    window.setTimeout(function(){
      row.remove();
      if(after) after();
    }, 200);
  }

  if(applicationsList){
    applicationsList.addEventListener('click', function(e){
      var acceptBtn = e.target.closest('.accept-btn');
      var declineBtn = e.target.closest('.decline-btn');
      if(!acceptBtn && !declineBtn) return;
      var row = e.target.closest('.invite-row');
      if(!row) return;
      var isAccept = !!acceptBtn;
      removeRow(row, function(){
        updateApplicationsUI();
        if(isAccept && joinedCountEl){
          joinedCountEl.textContent = String(parseInt(joinedCountEl.textContent, 10) + 1);
        }
      });
    });
  }

  if(sentList){
    sentList.addEventListener('click', function(e){
      var cancelBtn = e.target.closest('.cancel-btn');
      if(!cancelBtn) return;
      var row = e.target.closest('.invite-row');
      if(!row) return;
      removeRow(row, updateSentUI);
    });
  }

  if(inviteForm){
    inviteForm.addEventListener('submit', function(e){
      e.preventDefault();
      var name = inviteUsername.value.trim();
      if(!name){ inviteUsername.focus(); return; }
      var roleLabel = inviteRole.value === 'officer' ? 'Officer' : 'Soldier';

      var row = document.createElement('div');
      row.className = 'invite-row';
      row.innerHTML =
        '<span class="avatar avatar-md"></span>' +
        '<div class="invite-row-info">' +
          '<p class="invite-row-name"></p>' +
          '<p class="invite-row-meta"></p>' +
        '</div>' +
        '<div class="invite-row-actions">' +
          '<span class="invite-status is-pending">Pending</span>' +
          '<button type="button" class="link-btn cancel-btn">Cancel</button>' +
        '</div>';
      row.querySelector('.avatar').textContent = initials(name);
      row.querySelector('.invite-row-name').textContent = name;
      row.querySelector('.invite-row-meta').textContent = 'Invited as ' + roleLabel + ' · Sent just now';

      sentList.insertBefore(row, sentList.firstChild);
      updateSentUI();

      inviteUsername.value = '';
      inviteRole.value = 'soldier';
      inviteUsername.focus();
    });
  }

  updateApplicationsUI();
  updateSentUI();

})();
