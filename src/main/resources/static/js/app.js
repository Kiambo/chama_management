// Shared page behaviour. Pages work without it; it only adds conveniences.
(function () {
	// "← Back" in the breadcrumbs: returns to the page you actually came from, when that
	// was a page of this app. Hidden when there is nowhere to go back to.
	function addBackLink() {
		var crumbs = document.querySelector('nav.crumbs');
		if (!crumbs || !document.referrer) {
			return;
		}
		var from;
		try {
			from = new URL(document.referrer);
		} catch (e) {
			return;
		}
		var here = location.pathname + location.search;
		if (from.origin !== location.origin || from.pathname + from.search === here || from.pathname === '/login') {
			return;
		}
		var back = document.createElement('a');
		back.href = from.pathname + from.search;
		back.className = 'back';
		back.textContent = '← Back';
		back.addEventListener('click', function (e) {
			if (history.length > 1) {
				e.preventDefault();
				history.back();
			}
		});
		crumbs.appendChild(back);
	}

	// Payment method: a dropdown of common methods, plus "Other…" which shows a text box.
	// Whichever is in use carries the form field name, so the server gets one value.
	function setUpMethodPickers() {
		document.querySelectorAll('[data-method-picker]').forEach(function (picker) {
			var select = picker.querySelector('select');
			var other = picker.querySelector('input[type=text]');
			var name = select.dataset.name;
			function sync() {
				var isOther = select.value === '__other';
				other.hidden = !isOther;
				other.required = isOther;
				other.name = isOther ? name : '';
				select.name = isOther ? '' : name;
				if (isOther && document.activeElement === select) {
					other.focus();
				}
			}
			select.addEventListener('change', sync);
			sync();
		});
	}

	document.addEventListener('DOMContentLoaded', function () {
		addBackLink();
		setUpMethodPickers();
	});
})();
