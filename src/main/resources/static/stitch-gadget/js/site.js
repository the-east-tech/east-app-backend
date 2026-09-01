(() => {
  "use strict";

  document.documentElement.classList.add("js");
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const finePointer = window.matchMedia("(hover: hover) and (pointer: fine)").matches;
  const colours = ["#e399cc", "#c87eb5", "#ffffff", "#8f81d3", "#657bd4"];
  let tapTotal = 0;
  let scoreTimer = 0;

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

  const scrollProgress = document.querySelector(".scroll-progress span");
  let scrollTicking = false;
  const updateScrollProgress = () => {
    const distance = document.documentElement.scrollHeight - window.innerHeight;
    const progress = distance > 0 ? Math.min(1, Math.max(0, window.scrollY / distance)) : 0;
    if (scrollProgress) scrollProgress.style.transform = "scaleX(" + progress + ")";
    scrollTicking = false;
  };
  window.addEventListener("scroll", () => {
    if (!scrollTicking) {
      scrollTicking = true;
      requestAnimationFrame(updateScrollProgress);
    }
  }, { passive: true });
  updateScrollProgress();

  const ambientPointer = document.querySelector(".ambient-pointer");
  const hero = document.querySelector(".hero");
  let ambientTimer = 0;
  if (!reducedMotion && finePointer && ambientPointer) {
    window.addEventListener("pointermove", (event) => {
      ambientPointer.style.setProperty("--pointer-x", event.clientX - 95 + "px");
      ambientPointer.style.setProperty("--pointer-y", event.clientY - 95 + "px");
      ambientPointer.classList.add("is-visible");
      clearTimeout(ambientTimer);
      ambientTimer = window.setTimeout(() => ambientPointer.classList.remove("is-visible"), 1000);

      if (hero) {
        const rect = hero.getBoundingClientRect();
        if (event.clientY >= rect.top && event.clientY <= rect.bottom) {
          const x = (event.clientX / window.innerWidth - .5) * 18;
          const y = ((event.clientY - rect.top) / Math.max(rect.height, 1) - .5) * 12;
          hero.style.setProperty("--hero-x", x + "px");
          hero.style.setProperty("--hero-y", y + "px");
        }
      }
    }, { passive: true });
    document.documentElement.addEventListener("mouseleave", () => ambientPointer.classList.remove("is-visible"));
  }

  const createParticle = (x, y, spread, index) => {
    const particle = document.createElement("i");
    const angle = Math.random() * Math.PI * 2;
    const distance = spread * (.45 + Math.random() * .75);
    const isStar = index % 3 === 0;
    particle.className = "tap-particle" + (isStar ? " is-star" : "");
    if (isStar) particle.textContent = "✦";
    particle.style.left = x + "px";
    particle.style.top = y + "px";
    particle.style.setProperty("--dx", Math.cos(angle) * distance + "px");
    particle.style.setProperty("--dy", Math.sin(angle) * distance + "px");
    particle.style.setProperty("--spin", (Math.random() * 300 - 150) + "deg");
    particle.style.setProperty("--particle-size", (isStar ? 10 + Math.random() * 8 : 5 + Math.random() * 6) + "px");
    particle.style.setProperty("--particle-colour", colours[index % colours.length]);
    particle.style.setProperty("--particle-duration", (.55 + Math.random() * .32) + "s");
    document.body.appendChild(particle);
    particle.addEventListener("animationend", () => particle.remove(), { once: true });
  };

  const burstAt = (x, y, count = 7, spread = 70) => {
    if (reducedMotion) return;
    for (let index = 0; index < count; index += 1) {
      createParticle(x, y, spread, index);
    }
  };

  const showTapScore = () => {
    const score = document.getElementById("tapScore");
    const count = document.getElementById("tapCount");
    if (!score || !count) return;
    tapTotal += 1;
    count.textContent = String(tapTotal);
    score.classList.remove("is-visible");
    void score.offsetWidth;
    score.classList.add("is-visible");
    clearTimeout(scoreTimer);
    scoreTimer = window.setTimeout(() => score.classList.remove("is-visible"), 1450);
  };

  const popElement = (element, x, y, particleCount = 7) => {
    element.classList.remove("tap-pop");
    void element.offsetWidth;
    element.classList.add("tap-pop");
    burstAt(x, y, particleCount, particleCount > 12 ? 150 : 68);
    showTapScore();
    window.setTimeout(() => element.classList.remove("tap-pop"), 620);
  };

  document.querySelectorAll("[data-ripple]").forEach((element) => {
    element.addEventListener("pointerdown", (event) => {
      if (event.pointerType === "mouse" && event.button !== 0) return;
      const oldRipple = element.querySelector(":scope > .ripple");
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

  const playableSelector = [
    ".tap",
    ".feature-card",
    ".eligibility-item",
    ".reason-card",
    ".phone-card",
    ".zero-badge",
    ".contact-option",
    ".location-card",
    ".trust-list span",
    ".hero-trust span"
  ].join(",");

  document.querySelectorAll(playableSelector).forEach((element) => {
    element.classList.add("playable");
    let press = null;

    element.addEventListener("pointerdown", (event) => {
      if (event.pointerType === "mouse" && event.button !== 0) return;
      const nestedControl = event.target.closest("a,button");
      if (nestedControl && nestedControl !== element && !element.matches("a,button")) return;
      press = { id: event.pointerId, x: event.clientX, y: event.clientY };
      element.classList.add("is-pressed");
    });

    element.addEventListener("pointermove", (event) => {
      if (!press || press.id !== event.pointerId) return;
      if (Math.hypot(event.clientX - press.x, event.clientY - press.y) > 14) {
        press = null;
        element.classList.remove("is-pressed");
      }
    }, { passive: true });

    element.addEventListener("pointerup", (event) => {
      if (!press || press.id !== event.pointerId) return;
      const point = press;
      press = null;
      element.classList.remove("is-pressed");
      popElement(element, event.clientX || point.x, event.clientY || point.y);
    });

    element.addEventListener("pointercancel", () => {
      press = null;
      element.classList.remove("is-pressed");
    });

    element.addEventListener("click", (event) => {
      if (event.detail !== 0) return;
      const rect = element.getBoundingClientRect();
      popElement(element, rect.left + rect.width / 2, rect.top + rect.height / 2);
    });
  });

  if (!reducedMotion && finePointer) {
    document.querySelectorAll(".tilt-card").forEach((card) => {
      card.addEventListener("pointermove", (event) => {
        const rect = card.getBoundingClientRect();
        const x = (event.clientX - rect.left) / rect.width - 0.5;
        const y = (event.clientY - rect.top) / rect.height - 0.5;
        card.style.transform = "perspective(900px) rotateX(" + (-y * 3.5) + "deg) rotateY(" + (x * 3.5) + "deg) translateY(-2px)";
      });
      card.addEventListener("pointerleave", () => {
        card.style.transform = "";
        card.classList.remove("is-pressed");
      });
    });
  }

  const playButton = document.getElementById("playButton");
  const heroVisual = document.querySelector(".hero-visual");
  if (playButton && heroVisual) {
    playButton.addEventListener("click", () => {
      const rect = playButton.getBoundingClientRect();
      heroVisual.classList.remove("is-playing");
      void heroVisual.offsetWidth;
      heroVisual.classList.add("is-playing");
      burstAt(rect.left + rect.width / 2, rect.top + rect.height / 2, 28, 185);
      if ("vibrate" in navigator) navigator.vibrate(24);
      window.setTimeout(() => heroVisual.classList.remove("is-playing"), 1000);
    });
  }

  const price = document.querySelector(".price strong");
  if (price && !reducedMotion) {
    const target = Number(price.textContent.trim()) || 97;
    const startedAt = performance.now();
    price.textContent = "0";
    price.classList.add("counting");
    const countPrice = (now) => {
      const progress = Math.min(1, (now - startedAt) / 900);
      const eased = 1 - Math.pow(1 - progress, 3);
      price.textContent = String(Math.round(target * eased));
      if (progress < 1) {
        requestAnimationFrame(countPrice);
      } else {
        price.textContent = String(target);
        window.setTimeout(() => price.classList.remove("counting"), 750);
      }
    };
    requestAnimationFrame(countPrice);
  }

  const year = document.getElementById("year");
  if (year) year.textContent = new Date().getFullYear();
})();
