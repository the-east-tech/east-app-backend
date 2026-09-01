(() => {
  "use strict";

  document.documentElement.classList.add("js");
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  document.querySelectorAll("[data-delay]").forEach((element) => {
    element.style.setProperty("--reveal-delay", element.dataset.delay + "ms");
  });

  const revealElements = document.querySelectorAll(".reveal");
  if (!reducedMotion && "IntersectionObserver" in window) {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: "0px 0px -40px" });
    revealElements.forEach((element) => observer.observe(element));
  } else {
    revealElements.forEach((element) => element.classList.add("is-visible"));
  }

  document.querySelectorAll("[data-ripple]").forEach((element) => {
    element.addEventListener("pointerdown", (event) => {
      const oldRipple = element.querySelector(".ripple");
      if (oldRipple) oldRipple.remove();

      const rect = element.getBoundingClientRect();
      const size = Math.max(rect.width, rect.height);
      const ripple = document.createElement("span");
      ripple.className = "ripple";
      ripple.style.width = size + "px";
      ripple.style.height = size + "px";
      ripple.style.left = event.clientX - rect.left - size / 2 + "px";
      ripple.style.top = event.clientY - rect.top - size / 2 + "px";
      element.appendChild(ripple);
      ripple.addEventListener("animationend", () => ripple.remove(), { once: true });
    });
  });

  const supportsHover = window.matchMedia("(hover: hover) and (pointer: fine)").matches;
  if (!reducedMotion && supportsHover) {
    document.querySelectorAll(".tilt-card").forEach((card) => {
      card.addEventListener("pointermove", (event) => {
        const rect = card.getBoundingClientRect();
        const x = (event.clientX - rect.left) / rect.width - 0.5;
        const y = (event.clientY - rect.top) / rect.height - 0.5;
        card.style.transform = "perspective(900px) rotateX(" + (-y * 3.5) + "deg) rotateY(" + (x * 3.5) + "deg) translateY(-2px)";
      });
      card.addEventListener("pointerleave", () => {
        card.style.transform = "";
      });
    });
  }

  const year = document.getElementById("year");
  if (year) year.textContent = new Date().getFullYear();
})();
