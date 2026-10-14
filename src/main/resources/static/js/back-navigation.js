/**
 * CampusPulse - Global Back Navigation Controller
 *
 * Provides intelligent, safe client-side back navigation.
 * Validates history and referrer origins before using window.history.back(),
 * protecting against external redirects, auth loops, and missing history states
 * by gracefully falling back to role-appropriate server-defined endpoints.
 */
(function () {
    'use strict';

    if (window.__campusPulseBackNavInitialized) {
        return;
    }
    window.__campusPulseBackNavInitialized = true;

    /**
     * Checks whether the referrer belongs to the current application and is safe to return to.
     *
     * @param {string} referrer - document.referrer string
     * @returns {boolean} true if referrer is same-origin, not the current URL, and not a loop route
     */
    function isSafeInternalReferrer(referrer) {
        if (!referrer) {
            return false;
        }

        try {
            var refUrl = new URL(referrer, window.location.origin);
            var currentUrl = new URL(window.location.href);

            // 1. Strict origin check: must match current host, scheme, and port exactly
            if (refUrl.origin !== currentUrl.origin) {
                return false;
            }

            // 2. Prevent reloading or rewinding to the exact same path
            if (refUrl.pathname === currentUrl.pathname) {
                return false;
            }

            // 3. Prevent navigating back into sensitive or loop-prone endpoints
            var refPath = refUrl.pathname.toLowerCase();
            if (refPath.includes('/logout') || refPath.includes('/login?error')) {
                return false;
            }

            return true;
        } catch (e) {
            return false;
        }
    }

    /**
     * Click handler attached to all elements with [data-back-button].
     *
     * @param {MouseEvent} event
     */
    function handleBackClick(event) {
        var button = event.currentTarget;
        var fallback = button.getAttribute('data-fallback') || button.getAttribute('href') || '/';
        var forceFallback = button.getAttribute('data-force-fallback') === 'true';

        // 1. If explicitly forced to fallback (e.g. success confirmation or auth forms), navigate directly
        if (forceFallback) {
            return;
        }

        // 2. Check if browser history has depth and referrer is a verified internal page
        var canGoBack = (window.history.length > 1) && isSafeInternalReferrer(document.referrer);

        if (canGoBack) {
            event.preventDefault();
            window.history.back();
        } else {
            // If it's not a link or default was prevented, navigate directly to fallback
            if (button.tagName.toLowerCase() === 'button') {
                event.preventDefault();
                window.location.href = fallback;
            }
            // For standard <a> elements without safe history, natural href navigation proceeds
        }
    }

    /**
     * Attaches click handlers to any [data-back-button] elements in the DOM.
     */
    function attachBackNavHandlers() {
        var buttons = document.querySelectorAll('[data-back-button]');
        buttons.forEach(function (button) {
            button.removeEventListener('click', handleBackClick);
            button.addEventListener('click', handleBackClick);
        });
    }

    // Attach on initial load and handle dynamic DOM insertion if any
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', attachBackNavHandlers);
    } else {
        attachBackNavHandlers();
    }

    // Expose utility function on window for programmatic testing if needed
    window.CampusPulseBackNav = {
        isSafeInternalReferrer: isSafeInternalReferrer,
        handleBackClick: handleBackClick,
        attachBackNavHandlers: attachBackNavHandlers
    };
})();
