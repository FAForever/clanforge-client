(function(){
  'use strict';

  /* ---- roster search + role filter ---- */
  var searchInput = document.getElementById('memberSearch');
  var roleFilter = document.getElementById('roleFilter');
  var clearBtn = document.getElementById('clearFilters');
  var emptyState = document.getElementById('emptyState');
  var rows = Array.prototype.slice.call(document.querySelectorAll('.member-row'));
  var rosterGroup = document.querySelector('.roster-group');
  var memberCountEl = document.querySelector('.js-member-count');
  if(!searchInput || !roleFilter || !clearBtn || !emptyState) return;

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
