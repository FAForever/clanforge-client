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

  /* ---- roster search + role filter ---- */
  var searchInput = document.getElementById('memberSearch');
  var roleFilter = document.getElementById('roleFilter');
  var clearBtn = document.getElementById('clearFilters');
  var emptyState = document.getElementById('emptyState');
  var rows = Array.prototype.slice.call(document.querySelectorAll('.member-row'));
  var rosterGroup = document.querySelector('.roster-group');
  var memberCountEl = document.querySelector('.js-member-count');

  function applyFilter(){
    var q = searchInput.value.trim().toLowerCase();
    var role = roleFilter.value;
    var totalVisible = 0;

    rows.forEach(function(row){
      var name = row.getAttribute('data-name');
      var rrole = row.getAttribute('data-role');
      var matches = (!q || name.indexOf(q) !== -1) && (role === 'all' || rrole === role);
      row.classList.toggle('is-hidden', !matches);
      if(matches) totalVisible++;
    });

    if(rosterGroup) rosterGroup.classList.toggle('is-hidden', totalVisible === 0);
    if(memberCountEl) memberCountEl.textContent = totalVisible;

    emptyState.classList.toggle('is-visible', totalVisible === 0);
  }

  searchInput.addEventListener('input', applyFilter);
  roleFilter.addEventListener('change', applyFilter);
  clearBtn.addEventListener('click', function(){
    searchInput.value = '';
    roleFilter.value = 'all';
    applyFilter();
    searchInput.focus();
  });

  applyFilter(); // sync counts with actual markup on load

})();
