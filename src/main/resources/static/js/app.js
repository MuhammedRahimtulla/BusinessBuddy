/*
 * BusinessBuddy front-end behaviour. Served from the application (no inline scripts),
 * so the Content-Security-Policy can forbid inline JavaScript.
 *
 * - Mobile menu toggle
 * - "Add line" buttons that clone a form row and renumber its field names
 * - Confirmation before destructive actions (data-confirm)
 * - Disabling submit buttons after the first click (repeat submissions are also
 *   handled safely on the server)
 * - Charts drawn with Chart.js from data-* attributes; forecast series are dashed
 */
(function () {
  'use strict';

  // Mobile menu -----------------------------------------------------------------
  const toggle = document.getElementById('menu-toggle');
  const sidebar = document.getElementById('sidebar');
  if (toggle && sidebar) {
    toggle.addEventListener('click', function () {
      const open = sidebar.classList.toggle('open');
      toggle.setAttribute('aria-expanded', String(open));
    });
  }

  // Print a sale receipt ----------------------------------------------------------
  const printButton = document.getElementById('print-receipt');
  if (printButton) {
    printButton.addEventListener('click', function () { window.print(); });
  }

  // Highlight the current menu item --------------------------------------------
  document.querySelectorAll('.side-nav .nav-item').forEach(function (link) {
    const href = link.getAttribute('href');
    if (href && href !== '/' && window.location.pathname === href) {
      link.classList.add('active');
      link.setAttribute('aria-current', 'page');
    }
  });

  // Add line ---------------------------------------------------------------------
  document.querySelectorAll('[data-add-line]').forEach(function (button) {
    button.addEventListener('click', function () {
      const body = document.getElementById(button.getAttribute('data-add-line'));
      if (!body) return;
      const rows = body.querySelectorAll('tr');
      const template = rows[rows.length - 1];
      const clone = template.cloneNode(true);
      const index = rows.length;
      clone.querySelectorAll('input, select').forEach(function (field) {
        if (field.name) field.name = field.name.replace(/\[\d+\]/, '[' + index + ']');
        if (field.id) field.id = field.id.replace(/\d+/, String(index));
        if (field.tagName === 'SELECT') {
          field.selectedIndex = 0;
        } else if (field.type !== 'hidden') {
          field.value = field.getAttribute('data-default') || '';
        }
        field.classList.remove('is-invalid');
      });
      body.appendChild(clone);
    });
  });

  // Confirm destructive actions --------------------------------------------------
  document.querySelectorAll('form[data-confirm]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      if (!window.confirm(form.getAttribute('data-confirm'))) {
        event.preventDefault();
        event.stopImmediatePropagation();
      }
    });
  });

  // Prevent double submission ----------------------------------------------------
  document.querySelectorAll('form').forEach(function (form) {
    if (form.method.toLowerCase() !== 'post') return;
    form.addEventListener('submit', function (event) {
      const submitter = event.submitter;
      if (submitter && submitter.name) {
        const hidden = document.createElement('input');
        hidden.type = 'hidden';
        hidden.name = submitter.name;
        hidden.value = submitter.value;
        form.appendChild(hidden);
      }
      setTimeout(function () {
        form.querySelectorAll('button[type="submit"], button:not([type])').forEach(function (b) {
          b.disabled = true;
        });
      }, 0);
    });
  });

  // Charts ------------------------------------------------------------------------
  if (window.Chart) {
    const palette = ['#0E8574', '#C9A227', '#16305C', '#C4453C', '#8C9BB0'];
    document.querySelectorAll('canvas.bb-chart').forEach(function (canvas) {
      const labels = (canvas.dataset.labels || '').split('|').filter(function (s) { return s.length; });
      const datasets = [];
      for (let i = 1; i <= 4; i++) {
        const raw = canvas.dataset['s' + i];
        if (raw === undefined) continue;
        const dashed = canvas.dataset['s' + i + 'Dashed'] === 'true';
        datasets.push({
          label: canvas.dataset['s' + i + 'Label'] || ('Series ' + i),
          data: raw.split('|').map(function (v) { return v === '' ? null : Number(v); }),
          borderColor: palette[i - 1],
          backgroundColor: canvas.dataset.type === 'bar' ? palette[i - 1] : 'transparent',
          borderDash: dashed ? [6, 4] : [],
          tension: 0.25,
          spanGaps: true,
          pointRadius: labels.length > 40 ? 0 : 2
        });
      }
      new window.Chart(canvas, {
        type: canvas.dataset.type || 'line',
        data: { labels: labels, datasets: datasets },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          interaction: { mode: 'index', intersect: false },
          plugins: { legend: { position: 'bottom' } },
          scales: { y: { beginAtZero: true } }
        }
      });
    });
  }
})();
