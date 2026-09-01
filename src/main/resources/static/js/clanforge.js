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

  /* ---- clan directory: search + recruiting filter + sort ---- */
  var browseSearch = document.getElementById('browseSearch');
  var recruitFilter = document.getElementById('recruitFilter');
  var sortSelect = document.getElementById('sortSelect');
  var clanGrid = document.getElementById('clanGrid');
  var browseEmptyState = document.getElementById('emptyState');
  var showingCaption = document.getElementById('showingCaption');
  var visibleCountEl = document.getElementById('visibleCount');
  var clearBrowseBtn = document.getElementById('clearFilters');

  if(browseSearch && clanGrid){
    var clanCards = Array.prototype.slice.call(clanGrid.querySelectorAll('.clan-card'));

    function sortCards(){
      var sortVal = sortSelect.value;
      clanCards.sort(function(a, b){
        if(sortVal === 'members'){
          return parseInt(b.getAttribute('data-members'), 10) - parseInt(a.getAttribute('data-members'), 10);
        }
        if(sortVal === 'name'){
          return a.getAttribute('data-name').localeCompare(b.getAttribute('data-name'));
        }
        return parseInt(a.getAttribute('data-rank'), 10) - parseInt(b.getAttribute('data-rank'), 10);
      });
      clanCards.forEach(function(card){ clanGrid.appendChild(card); });
    }

    function applyBrowseFilter(){
      var q = browseSearch.value.trim().toLowerCase();
      var recruiting = recruitFilter.value;
      var totalVisible = 0;

      sortCards();

      clanCards.forEach(function(card){
        var name = card.getAttribute('data-name');
        var rec = card.getAttribute('data-recruiting');
        var matches = (!q || name.indexOf(q) !== -1) && (recruiting === 'all' || rec === recruiting);
        card.classList.toggle('is-hidden', !matches);
        if(matches) totalVisible++;
      });

      browseEmptyState.classList.toggle('is-visible', totalVisible === 0);
      if(showingCaption) showingCaption.classList.toggle('is-hidden', totalVisible === 0);
      if(visibleCountEl) visibleCountEl.textContent = totalVisible;
    }

    browseSearch.addEventListener('input', applyBrowseFilter);
    recruitFilter.addEventListener('change', applyBrowseFilter);
    sortSelect.addEventListener('change', applyBrowseFilter);
    if(clearBrowseBtn){
      clearBrowseBtn.addEventListener('click', function(){
        browseSearch.value = '';
        recruitFilter.value = 'all';
        applyBrowseFilter();
        browseSearch.focus();
      });
    }

    applyBrowseFilter();
  }

})();
