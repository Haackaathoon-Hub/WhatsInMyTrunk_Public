(function () {
  'use strict';

  /* ========================================================================
     [링크·영상 임베드 설정] 여기에 GitHub · 시연 영상 · YouTube 쇼츠 주소를 입력하세요.
     - demoVideoUrl / youtubeShortsUrl 에 YouTube 일반 주소(/watch?v=..., /shorts/..., youtu.be/...)
       또는 /embed/... 주소, 로컬 .mp4 경로를 넣으면 하차 팝업 안에 자동으로 영상이 임베드됩니다.
     ======================================================================== */
  const PRE_EXIT_POPUP_CONFIG = {
    teamName: 'Players',
    githubUrl: 'https://github.com/Haackaathoon-Hub/WhatsInMyTrunk_Public',
    demoVideoUrl: 'https://youtu.be/9ibNaVEvJb4',
    cardNews1Url: 'https://youtu.be/Ee-gYc9_pIw',
    cardNews2Url: 'https://youtu.be/uv1dv7sx3kk',
    youtubeShortsUrl: 'https://youtube.com/shorts/AY4PW7mkSdg',
    apkUrl: 'https://github.com/Haackaathoon-Hub/WhatsInMyTrunk_Public/releases/tag/v1.0.0'
  };

  /* ========================================================================
     1. Pleos Dual Theme Toggle (Night Dark vs Day Light HMI)
     ======================================================================== */
  const htmlEl = document.documentElement;
  const themeToggle = document.getElementById('themeToggle');
  const themeIcon = document.getElementById('themeIcon');
  const themeLabel = document.getElementById('themeLabel');

  function applyTheme(theme) {
    htmlEl.setAttribute('data-theme', theme);
    if (theme === 'light') {
      themeIcon.textContent = '☀️';
      themeLabel.textContent = 'Day';
    } else {
      themeIcon.textContent = '🌙';
      themeLabel.textContent = 'Night';
    }
  }

  themeToggle.addEventListener('click', () => {
    const next = htmlEl.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    applyTheme(next);
  });

  /* ========================================================================
     2. Top Route Navigation Scroll-Spy, Header Observer & Journey Capsule
     ======================================================================== */
  const siteHeader = document.getElementById('top');
  const routeStops = Array.from(document.querySelectorAll('.route-stop'));
  const routeTrackFill = document.getElementById('routeTrackFill');
  const trackedSectionIds = ['hero-origin', 'transport-core', 'architecture', 'download'];

  const routeCapsuleDock = document.getElementById('routeCapsuleDock');
  const routeCapsuleBtn = document.getElementById('routeCapsuleBtn');
  // [비활성화] 캡슐 안내 팝오버 DOM 참조
  // const routePopover = document.getElementById('routePopover');
  const capsuleLabelStage = document.getElementById('capsuleLabelStage');
  const capsuleLabelSlot = document.getElementById('capsuleLabelSlot');
  const capsuleTag = document.getElementById('capsuleTag');
  const capsuleName = document.getElementById('capsuleName');
  const capsulePct = document.getElementById('capsulePct');
  // const popoverProgress = document.getElementById('popoverProgress');
  // const popoverStops = Array.from(document.querySelectorAll('.popover-stop'));

  const WAYPOINT_META = [
    { tag: '출발지', name: '콕핏 체험', phase: 'origin' },
    { tag: '적재 인지', name: '출발·도착 관리', phase: 'transit' },
    { tag: '맥락 엔진', name: '4-Layer 흐름', phase: 'transit' },
    { tag: '목적지', name: '하차·다운로드', phase: 'dest' }
  ];

  // 미세조정용 상수
  // const POPOVER_CLOSE_SCROLL_DELTA = 80;
  const SEED_DOT_HOLD_MS = 500;
  const CAPSULE_EXPAND_MS = 460;
  // let popoverOpenScrollY = 0;
  let lastActiveIdx = -1;
  let ghostCleanupTimer = null;
  let seedHoldTimer = null;
  let expandCleanupTimer = null;
  let isCapsuleVisible = false;

  function cancelCapsuleMorph() {
    if (seedHoldTimer) {
      clearTimeout(seedHoldTimer);
      seedHoldTimer = null;
    }
    if (expandCleanupTimer) {
      clearTimeout(expandCleanupTimer);
      expandCleanupTimer = null;
    }
    routeCapsuleBtn.classList.remove('is-seed-dot', 'is-expanding');
    routeCapsuleBtn.style.width = '';
  }

  function playBlueDotToCapsule() {
    cancelCapsuleMorph();
    syncCapsuleWidth();

    // Measure natural expanded width while dock is still hidden (opacity: 0)
    const targetWidth = Math.ceil(routeCapsuleBtn.offsetWidth);
    if (targetWidth <= 54) {
      document.body.classList.add('is-scrolled-header');
      return;
    }

    // Phase 1: Lock 54x54 blue dot dead-center BEFORE revealing dock
    routeCapsuleBtn.classList.add('is-seed-dot');
    void routeCapsuleBtn.offsetWidth;
    document.body.classList.add('is-scrolled-header');

    // Phase 2: Hold briefly as centered blue dot, then unfold symmetrically into full capsule
    seedHoldTimer = setTimeout(() => {
      seedHoldTimer = null;
      routeCapsuleBtn.style.width = '54px';
      routeCapsuleBtn.classList.remove('is-seed-dot');
      routeCapsuleBtn.classList.add('is-expanding');
      void routeCapsuleBtn.offsetWidth;
      routeCapsuleBtn.style.width = `${targetWidth}px`;

      expandCleanupTimer = setTimeout(() => {
        expandCleanupTimer = null;
        routeCapsuleBtn.classList.remove('is-expanding');
        routeCapsuleBtn.style.width = '';
      }, CAPSULE_EXPAND_MS);
    }, SEED_DOT_HOLD_MS);
  }

  function syncCapsuleWidth() {
    if (!capsuleLabelStage || !capsuleLabelSlot) return;
    const targetWidth = Math.ceil(capsuleLabelSlot.offsetWidth);
    if (targetWidth > 0) {
      capsuleLabelStage.style.width = `${targetWidth}px`;
    }
  }

  function transitionCapsuleLabel(nextIdx, animate) {
    const meta = WAYPOINT_META[nextIdx];
    routeCapsuleBtn.setAttribute('data-phase', meta.phase);

    if (!animate || lastActiveIdx === -1) {
      capsuleTag.textContent = meta.tag;
      capsuleName.textContent = meta.name;
      syncCapsuleWidth();
      lastActiveIdx = nextIdx;
      return;
    }

    const goingDown = nextIdx > lastActiveIdx;
    lastActiveIdx = nextIdx;

    // 1. Clean up any existing ghost if user scrolls rapidly
    if (ghostCleanupTimer) {
      clearTimeout(ghostCleanupTimer);
      ghostCleanupTimer = null;
    }
    capsuleLabelStage.querySelectorAll('.capsule-label-ghost').forEach((g) => g.remove());

    // 2. Create outgoing ghost snapshot of current text
    const ghost = capsuleLabelSlot.cloneNode(true);
    ghost.removeAttribute('id');
    ghost.querySelectorAll('[id]').forEach((el) => el.removeAttribute('id'));
    ghost.className = 'capsule-label-ghost';
    ghost.setAttribute('aria-hidden', 'true');
    capsuleLabelStage.appendChild(ghost);

    // 3. Prep incoming live slot off-axis & swap text + target width
    capsuleLabelSlot.classList.remove('is-prep-down', 'is-prep-up');
    capsuleLabelSlot.classList.add(goingDown ? 'is-prep-down' : 'is-prep-up');
    capsuleTag.textContent = meta.tag;
    capsuleName.textContent = meta.name;

    // Force single layout read to lock start position and measure new text width
    void capsuleLabelSlot.offsetWidth;
    syncCapsuleWidth();

    // 4. Trigger synchronized leave + enter transitions on next frame
    requestAnimationFrame(() => {
      ghost.classList.add(goingDown ? 'is-leave-up' : 'is-leave-down');
      capsuleLabelSlot.classList.remove('is-prep-down', 'is-prep-up');
    });

    ghostCleanupTimer = setTimeout(() => {
      ghost.remove();
      ghostCleanupTimer = null;
    }, 360);
  }

  /* [비활성화] 캡슐 클릭 안내 팝오버 제어 로직 (충돌 방지용 주석 처리)
  function setPopoverOpen(isOpen) {
    routePopover.hidden = !isOpen;
    routeCapsuleBtn.setAttribute('aria-expanded', String(isOpen));
    if (isOpen) {
      popoverOpenScrollY = window.scrollY;
    }
  }

  let isPopoverNavigating = false;
  let popoverNavSettleTimer = null;

  function beginPopoverNavigation() {
    isPopoverNavigating = true;
    popoverOpenScrollY = window.scrollY;
    if (popoverNavSettleTimer) clearTimeout(popoverNavSettleTimer);
    popoverNavSettleTimer = setTimeout(() => {
      isPopoverNavigating = false;
      popoverOpenScrollY = window.scrollY;
      popoverNavSettleTimer = null;
    }, 900);
  }
  */

  // 옵션 A: 데스크톱/모바일 공통 — 초기 헤더(.site-header)가 뷰포트 상단 밖으로 밀려나면 중앙 푸른 점 → 캡슐 전환
  if ('IntersectionObserver' in window && siteHeader) {
    const headerObserver = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        const passedTop = !entry.isIntersecting && entry.boundingClientRect.top < 0;
        if (passedTop && !isCapsuleVisible) {
          isCapsuleVisible = true;
          playBlueDotToCapsule();
        } else if (!passedTop && isCapsuleVisible) {
          isCapsuleVisible = false;
          document.body.classList.remove('is-scrolled-header');
          cancelCapsuleMorph();
          /* [비활성화] 캡슐 안내 팝오버 닫기
          if (!routePopover.hidden) {
            setPopoverOpen(false);
          }
          */
        }
      },
      { threshold: 0 }
    );
    headerObserver.observe(siteHeader);
  }

  function updateRouteProgress() {
    const scrollPos = window.scrollY + window.innerHeight * 0.36;
    let activeIdx = 0;

    trackedSectionIds.forEach((id, idx) => {
      const el = document.getElementById(id);
      if (el && scrollPos >= el.offsetTop) {
        activeIdx = idx;
      }
    });

    const maxScroll = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
    if (window.scrollY >= maxScroll - 40) {
      activeIdx = trackedSectionIds.length - 1;
    }

    const continuousPct = Math.min(100, Math.max(0, Math.round((window.scrollY / maxScroll) * 100)));

    routeStops.forEach((stop, idx) => {
      stop.classList.toggle('active', idx === activeIdx);
      stop.classList.toggle('passed', idx < activeIdx);
    });

    const trackPct = (activeIdx / (routeStops.length - 1)) * 100;
    routeTrackFill.style.width = `${trackPct}%`;

    // Sync Journey Capsule (smooth directional blur-slide + width morphing only when index changes)
    if (activeIdx !== lastActiveIdx) {
      transitionCapsuleLabel(activeIdx, lastActiveIdx !== -1);
    }
    capsulePct.textContent = `${continuousPct}%`;

    /* [비활성화] 캡슐 안내 팝오버 상태 동기화 및 자동 접힘
    popoverProgress.textContent = `${continuousPct}% 이동 중`;

    popoverStops.forEach((item, idx) => {
      const isActive = idx === activeIdx;
      const isPassed = idx < activeIdx;
      item.classList.toggle('active', isActive);
      item.classList.toggle('passed', isPassed);
      const stateEl = item.querySelector('.popover-stop-state');
      if (stateEl) {
        stateEl.textContent = isActive ? '현재 지점' : isPassed ? '통과' : '';
      }
    });

    // 목적지 클릭 이동 중에는 팝오버를 유지하고, 사용자가 직접 일정 거리 이상 스크롤할 때만 접힘
    if (isPopoverNavigating) {
      popoverOpenScrollY = window.scrollY;
      if (popoverNavSettleTimer) clearTimeout(popoverNavSettleTimer);
      popoverNavSettleTimer = setTimeout(() => {
        isPopoverNavigating = false;
        popoverOpenScrollY = window.scrollY;
        popoverNavSettleTimer = null;
      }, 180);
    } else if (!routePopover.hidden && Math.abs(window.scrollY - popoverOpenScrollY) > POPOVER_CLOSE_SCROLL_DELTA) {
      setPopoverOpen(false);
    }
    */
  }

  if (document.fonts && document.fonts.ready) {
    document.fonts.ready.then(syncCapsuleWidth);
  }

  /* [비활성화] 캡슐 클릭 안내 팝오버 이벤트 리스너
  routeCapsuleBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    setPopoverOpen(routePopover.hidden);
  });

  popoverStops.forEach((stop) => {
    stop.addEventListener('click', () => {
      beginPopoverNavigation();
    });
  });

  ['wheel', 'touchstart'].forEach((evt) => {
    window.addEventListener(
      evt,
      () => {
        if (isPopoverNavigating) {
          isPopoverNavigating = false;
          if (popoverNavSettleTimer) {
            clearTimeout(popoverNavSettleTimer);
            popoverNavSettleTimer = null;
          }
          popoverOpenScrollY = window.scrollY;
        }
      },
      { passive: true }
    );
  });

  document.addEventListener('pointerdown', (e) => {
    if (!routePopover.hidden && !routeCapsuleDock.contains(e.target)) {
      setPopoverOpen(false);
    }
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !routePopover.hidden) {
      setPopoverOpen(false);
    }
  });
  */

  window.addEventListener('scroll', updateRouteProgress, { passive: true });
  window.addEventListener('resize', () => {
    syncCapsuleWidth();
    updateRouteProgress();
  });
  updateRouteProgress();

  /* ========================================================================
     3. Hero Interactive Cockpit Preview
     ======================================================================== */
  const HERO_PRESETS = {
    mart: [
      { emoji: '❄️', name: '신선식품 · 냉동만두', tag: '신선식품 30분', perishable: true, meta: '이마트 성수점 · 14:42 적재' },
      { emoji: '🛍️', name: '다회용 장바구니', tag: '상시 보관', perishable: false, meta: '트렁크 좌측 수납함' }
    ],
    cake: [
      { emoji: '🎂', name: '생크림 홀케이크', tag: '파손·온도 주의', perishable: true, meta: '성수 디저트 · 완만 주행 권장' },
      { emoji: '🎁', name: '선물 쇼핑백', tag: '트렁크 적재', perishable: false, meta: '14:55 적재 완료' }
    ],
    golf: [
      { emoji: '⛳', name: '골프 캐디백 세트', tag: '레저 장비', perishable: false, meta: '트렁크 메인 데크' },
      { emoji: '👟', name: '골프화 파우치', tag: '상시 보관', perishable: false, meta: '트렁크 우측 포켓' }
    ]
  };

  const heroState = {
    preset: 'mart',
    trunkOpen: false,
    temp: 22.0,
    gear: 'P',
    confirmed: false
  };

  const heroCargoCards = document.getElementById('heroCargoCards');
  const heroCargoCounter = document.getElementById('heroCargoCounter');
  const heroTailgateBadge = document.getElementById('heroTailgateBadge');
  const heroTailgateText = document.getElementById('heroTailgateText');
  const heroTempPill = document.getElementById('heroTempPill');
  const heroGearPill = document.getElementById('heroGearPill');
  const heroAlertBadge = document.getElementById('heroAlertBadge');
  const heroAlertTitle = document.getElementById('heroAlertTitle');
  const heroOpenTrunkBtn = document.getElementById('heroOpenTrunkBtn');
  const heroConfirmBtn = document.getElementById('heroConfirmBtn');
  const heroHeatBtn = document.getElementById('heroHeatBtn');
  const heroToggleDoorBtn = document.getElementById('heroToggleDoorBtn');

  function renderHeroCockpit() {
    const items = HERO_PRESETS[heroState.preset];
    heroCargoCards.innerHTML = items
      .map(
        (item) => `
      <div class="cargo-mini-card">
        <div class="cargo-mini-top">
          <span class="cargo-emoji">${item.emoji}</span>
          <span class="cargo-tag ${item.perishable ? 'perishable' : ''}">${item.tag}</span>
        </div>
        <div class="cargo-mini-name">${item.name}</div>
        <div class="cargo-mini-meta">${item.meta}</div>
      </div>`
      )
      .join('');

    heroCargoCounter.textContent = `적재 화물 ${items.length}건 보관 중`;

    if (heroState.trunkOpen) {
      heroTailgateBadge.classList.add('open');
      heroTailgateText.textContent = '테일게이트 열림';
      heroOpenTrunkBtn.textContent = '트렁크 닫기';
    } else {
      heroTailgateBadge.classList.remove('open');
      heroTailgateText.textContent = '테일게이트 닫힘 (안전)';
      heroOpenTrunkBtn.textContent = '트렁크 열기';
    }

    heroGearPill.textContent = `GEAR ${heroState.gear}`;
    heroTempPill.textContent = `${heroState.temp.toFixed(1)}℃`;
    heroTempPill.classList.toggle('hot', heroState.temp >= 26.0);

    if (heroState.confirmed) {
      heroAlertBadge.textContent = '확인 완료 · 대기 모드';
      heroAlertTitle.textContent = '하차 안내가 완료되었습니다. 안전한 이동 되세요.';
    } else if (heroState.temp >= 26.0 && heroState.preset !== 'golf') {
      heroAlertBadge.textContent = '⚠️ 고온 변질 주의 · 28.5℃ 감지';
      heroAlertTitle.textContent = `${items[0].name} 신선도 보호를 위해 트렁크를 먼저 확인하세요.`;
    } else {
      heroAlertBadge.textContent = '목적지 도착 · P단 하차 안내';
      heroAlertTitle.textContent = `트렁크에 ${items[0].name} 외 ${items.length - 1}건이 보관되어 있습니다.`;
    }

    document.querySelectorAll('[data-hero-preset]').forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.heroPreset === heroState.preset);
    });
    heroHeatBtn.classList.toggle('active', heroState.temp >= 26.0);
    heroToggleDoorBtn.classList.toggle('active', heroState.trunkOpen);
  }

  document.querySelectorAll('[data-hero-preset]').forEach((btn) => {
    btn.addEventListener('click', () => {
      heroState.preset = btn.dataset.heroPreset;
      heroState.confirmed = false;
      renderHeroCockpit();
    });
  });

  heroHeatBtn.addEventListener('click', () => {
    heroState.temp = heroState.temp >= 26.0 ? 22.0 : 28.5;
    heroState.confirmed = false;
    renderHeroCockpit();
  });

  heroToggleDoorBtn.addEventListener('click', () => {
    heroState.trunkOpen = !heroState.trunkOpen;
    renderHeroCockpit();
  });

  heroOpenTrunkBtn.addEventListener('click', () => {
    heroState.trunkOpen = !heroState.trunkOpen;
    renderHeroCockpit();
  });

  heroConfirmBtn.addEventListener('click', () => {
    heroState.confirmed = true;
    heroState.trunkOpen = false;
    renderHeroCockpit();
  });

  renderHeroCockpit();

  /* ========================================================================
     4. Origin -> Transit -> Destination Random Straight-Line Map + Follow-Cam
     ======================================================================== */
  const odScrollyTrack = document.getElementById('odScrollyTrack');
  const odStickyStage = document.getElementById('odStickyStage');
  const odGridStage = document.getElementById('odGridStage');
  const odGridPlane = document.getElementById('odGridPlane');
  const odAppHudPanel = document.getElementById('odAppHudPanel');
  const transportCoreEl = document.getElementById('transport-core');
  const archSectionEl = document.getElementById('architecture');
  const odStraightMapSvg = document.getElementById('odStraightMapSvg');
  const odHumanMapSvg = document.getElementById('odHumanMapSvg');
  const odCargoMapSvg = document.getElementById('odCargoMapSvg');
  const odBadgeRoute = document.getElementById('odBadgeRoute');
  const odBadgeRouteText = document.getElementById('odBadgeRouteText');
  const odBadgeHuman = document.getElementById('odBadgeHuman');
  const odBadgeHumanText = document.getElementById('odBadgeHumanText');
  const odBadgeCargo = document.getElementById('odBadgeCargo');
  const odBadgeCargoText = document.getElementById('odBadgeCargoText');
  const odTrackHuman = document.getElementById('odTrackHuman');
  const odHumanOrigin = document.getElementById('odHumanOrigin');
  const odHumanDest = document.getElementById('odHumanDest');
  const odTrackCargo = document.getElementById('odTrackCargo');
  const odCargoOrigin = document.getElementById('odCargoOrigin');
  const odCargoDest = document.getElementById('odCargoDest');
  const odAlertCount = document.getElementById('odAlertCount');
  const odAlertLogList = document.getElementById('odAlertLogList');

  let lastHudKey = '';
  let lastLogKey = '';
  let lastTopAlarmId = '';
  let nextSpanSeq = 1;
  let alarmEvents = [];
  let phase2MilestoneAdded = false;

  let odCameraWorld = null;
  let odHumanCamWorld = null;
  let odCargoCamWorld = null;
  let odGreyRoadsGroup = null;
  let odHumanRoutesGroup = null;
  let odCargoRoutesGroup = null;
  let blueRouteEl = null;
  let blueRouteGlowEl = null;

  const TILE_W = 1040;
  const TILE_H = 660;
  const VIEW_W = 1000;
  const VIEW_H = 600;

  const HUMAN_SCENARIOS = [
    { origin: '잠실 단지', dest: '강남 사거리', startMsg: '잠실→강남 이동 추적 시작', endMsg: '강남 도착 · 하차 전 안전 알람' },
    { origin: '판교 밸리', dest: '정자 환승역', startMsg: '판교→정자 동승 경로 인지', endMsg: '정자역 도착 · 환승 하차 알람' },
    { origin: '여의도', dest: '공덕 주차장', startMsg: '여의도→공덕 귀가 동선 추적', endMsg: '공덕 도착 · 후석 확인 알람' },
    { origin: '서초교', dest: '반포 단지', startMsg: '서초→반포 픽업 이동 인지', endMsg: '반포 도착 · 안전 하차 알람' },
    { origin: '성수동', dest: '서울숲 주차장', startMsg: '성수→서울숲 도보 연계 추적', endMsg: '서울숲 도착 · 주차 구역 알람' }
  ];

  const CARGO_SCENARIOS = [
    { origin: '자택 현관', dest: '판교 오피스', startMsg: '[노트북] 자택→판교 적재 추적', endMsg: '판교 도착 · 노트북 챙김 알람' },
    { origin: '강남 마트', dest: '분당 아파트', startMsg: '[신선식품] 마트→분당 냉장 추적', endMsg: '분당 도착 · 식품 즉시 하차 알람' },
    { origin: '수납함', dest: '광화문', startMsg: '[장우산] 적재 · 광화문 강수 연동', endMsg: '광화문 도착 · 비 예보 우산 알람' },
    { origin: '스포츠센터', dest: '한강 반포', startMsg: '[러닝백] 센터→반포 이동 추적', endMsg: '반포 도착 · 러닝백 하차 알람' },
    { origin: '청담 베이커리', dest: '이태원', startMsg: '[케이크] 청담→이태원 수평 감시', endMsg: '이태원 도착 · 케이크 망각 알람' }
  ];

  // Randomized overlay intervals along the blue route (bounded length & regular cadence)
  const HUMAN_SPAN_CONFIG = {
    type: 'human',
    tag: '사람',
    minLen: 68,
    maxLen: 165,
    minGap: 95,
    maxGap: 185,
    initialOffsetMin: 36,
    initialOffsetMax: 90,
    className: 'od-human-route',
    scenarios: HUMAN_SCENARIOS
  };
  const CARGO_SPAN_CONFIG = {
    type: 'cargo',
    tag: '물건',
    minLen: 38,
    maxLen: 105,
    minGap: 110,
    maxGap: 210,
    initialOffsetMin: 75,
    initialOffsetMax: 160,
    className: 'od-cargo-route',
    scenarios: CARGO_SCENARIOS
  };

  /* ========================================================================
     [2번 섹션 지도·카메라 속도 및 거리 기준 고정 해제 파라미터]
     - baseSvgPerScrollPx     : 1.0배 기준 스크롤 1px당 점/줄 이동 거리 (SVG 단위, 기본 1.0)
     - scrollLineSpeedScale   : 스크롤 속도 배율 (작을수록 천천히 가고, 그만큼 트랙 길이가 자동 연장됨)
     - unlockRouteMultiplier  : 고정이 풀리기 전까지 점이 이동해야 할 초기 경로 대비 거리 배율 (기본 1.0)
     - unlockTravelDist       : 0보다 크면 배율 대신 해당 SVG 이동 거리 도달 시 고정 해제 (예: 1200)
     - camFollowStartScrollP  : 카메라가 줄을 따라가기 시작하는 스크롤 진행률 (0~1, 기본 0.32)
     - panelGuardXRatio       : 줄 끝 X가 이 비율을 넘기 전에 패널 회피 추적 (기본 0.52)
     - panelGuardYRatio       : 줄 끝 Y가 이 비율 위로 올라가기 전에 패널 회피 추적 (기본 0.45)
     - camFocusX / camFocusY  : 추적 시 줄 끝이 화면에 머무는 기준 좌표 (1000x600 SVG 기준, 좌하단 여백 확보)
     - camFollowSmoothness    : 카메라 따라가는 반응 속도 (클수록 즉각적, 기본 4.8)
     - autoCruiseStartScrollP : 목표 거리 도달 후 연속 자동 주행 전환 시점 (0~1, 기본 0.88)
     - autoCruiseSpeed        : 연속 자동 주행 시 줄 전진 속도(px/sec, 기본 74)
     ======================================================================== */
  const OD_MOTION_CONFIG = {
    baseSvgPerScrollPx: 1.0,
    scrollLineSpeedScale: 0.20,
    unlockRouteMultiplier: 0.30,
    unlockTravelDist: 0,
    camFollowStartScrollP: 0.32,
    panelGuardXRatio: 0.52,
    panelGuardYRatio: 0.45,
    camFocusX: 400,
    camFocusY: 340,
    camFollowSmoothness: 4.8,
    autoCruiseStartScrollP: 0.88,
    autoCruiseSpeed: 74
  };

  let segments = [];
  let loadedTiles = new Set();
  let borderKeys = new Set();
  let routePts = [];
  let routeCumLens = [0];
  let routeTotalLen = 0;
  let phase1Len = 0;
  let urbanAngle = 0;

  let humanSpans = [];
  let cargoSpans = [];
  let nextHumanCursor = 0;
  let nextCargoCursor = 0;

  let scrollP = 0;
  let isStageVisible = false;
  let blueDist = 24;
  let camX = 0;
  let camY = 0;
  let animRafId = null;
  let lastFrameTs = 0;

  function clamp01(val) {
    return Math.min(1, Math.max(0, val));
  }

  function randBetween(min, max) {
    return min + Math.random() * (max - min);
  }

  function cityToSvg(u, v) {
    const cosA = Math.cos(urbanAngle);
    const sinA = Math.sin(urbanAngle);
    return {
      x: VIEW_W * 0.5 + u * cosA - v * sinA,
      y: VIEW_H * 0.5 + u * sinA + v * cosA
    };
  }

  function svgToCity(x, y) {
    const dx = x - VIEW_W * 0.5;
    const dy = y - VIEW_H * 0.5;
    const cosA = Math.cos(-urbanAngle);
    const sinA = Math.sin(-urbanAngle);
    return {
      u: dx * cosA - dy * sinA,
      v: dx * sinA + dy * cosA
    };
  }

  function appendRoadLine(s) {
    if (!odGreyRoadsGroup) return null;
    const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    line.setAttribute('x1', s.x1.toFixed(1));
    line.setAttribute('y1', s.y1.toFixed(1));
    line.setAttribute('x2', s.x2.toFixed(1));
    line.setAttribute('y2', s.y2.toFixed(1));
    line.setAttribute('class', s.tier);
    odGreyRoadsGroup.appendChild(line);
    s.el = line;
    return line;
  }

  function addCitySegment(u1, v1, u2, v2, tier, tileKey) {
    const p1 = cityToSvg(u1, v1);
    const p2 = cityToSvg(u2, v2);
    const seg = { x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y, tier, tileKey, el: null };
    appendRoadLine(seg);
    segments.push(seg);
  }

  function addBorderOnce(isVertical, fixedCoord, a, b, tier, tileKey) {
    const key = `${isVertical ? 'V' : 'H'}:${Math.round(fixedCoord)}:${Math.round(Math.min(a, b))}:${Math.round(Math.max(a, b))}`;
    if (borderKeys.has(key)) return;
    borderKeys.add(key);
    if (isVertical) addCitySegment(fixedCoord, a, fixedCoord, b, tier, tileKey);
    else addCitySegment(a, fixedCoord, b, fixedCoord, tier, tileKey);
  }

  function ensureTileBSP(tx, ty) {
    const tileKey = `${tx},${ty}`;
    if (loadedTiles.has(tileKey)) return;
    loadedTiles.add(tileKey);

    const uMin = tx * TILE_W - TILE_W * 0.5;
    const uMax = uMin + TILE_W;
    const vMin = ty * TILE_H - TILE_H * 0.5;
    const vMax = vMin + TILE_H;

    // Shared clean perimeter boulevards (deduplicated across neighboring tiles)
    addBorderOnce(false, vMin, uMin, uMax, 'straight-road-mid', tileKey);
    addBorderOnce(false, vMax, uMin, uMax, 'straight-road-mid', tileKey);
    addBorderOnce(true, uMin, vMin, vMax, 'straight-road-mid', tileKey);
    addBorderOnce(true, uMax, vMin, vMax, 'straight-road-mid', tileKey);

    // Cohesive orthogonal BSP block subdivision inside tile
    const blocks = [{ u0: uMin, v0: vMin, u1: uMax, v1: vMax, depth: 0 }];
    const MIN_SPLIT_W = 230;
    const MIN_SPLIT_H = 185;
    const MAX_SPLITS = 22;

    for (let s = 0; s < MAX_SPLITS; s++) {
      let bestIdx = -1;
      let bestScore = -1;
      for (let i = 0; i < blocks.length; i++) {
        const b = blocks[i];
        const w = b.u1 - b.u0;
        const h = b.v1 - b.v0;
        if (w < MIN_SPLIT_W && h < MIN_SPLIT_H) continue;
        const area = w * h * randBetween(0.85, 1.15);
        if (area > bestScore) {
          bestScore = area;
          bestIdx = i;
        }
      }
      if (bestIdx === -1) break;

      const b = blocks.splice(bestIdx, 1)[0];
      const w = b.u1 - b.u0;
      const h = b.v1 - b.v0;
      const tier = b.depth <= 1 ? 'straight-road-major' : b.depth <= 3 ? 'straight-road-mid' : 'straight-road-minor';

      const splitVertical = w >= MIN_SPLIT_W && (h < MIN_SPLIT_H || w / h >= 1.25);
      if (splitVertical) {
        const splitU = Math.round(b.u0 + w * randBetween(0.38, 0.62));
        addCitySegment(splitU, b.v0, splitU, b.v1, tier, tileKey);
        blocks.push({ u0: b.u0, v0: b.v0, u1: splitU, v1: b.v1, depth: b.depth + 1 });
        blocks.push({ u0: splitU, v0: b.v0, u1: b.u1, v1: b.v1, depth: b.depth + 1 });
      } else {
        const splitV = Math.round(b.v0 + h * randBetween(0.38, 0.62));
        addCitySegment(b.u0, splitV, b.u1, splitV, tier, tileKey);
        blocks.push({ u0: b.u0, v0: b.v0, u1: b.u1, v1: splitV, depth: b.depth + 1 });
        blocks.push({ u0: b.u0, v0: splitV, u1: b.u1, v1: b.v1, depth: b.depth + 1 });
      }
    }
  }

  function buildRoadGraph(segList) {
    const vertices = [];
    const findOrAddVertex = (x, y) => {
      for (let i = 0; i < vertices.length; i++) {
        if ((vertices[i].x - x) ** 2 + (vertices[i].y - y) ** 2 < 9) {
          return i;
        }
      }
      vertices.push({ x, y });
      return vertices.length - 1;
    };

    const segSplits = segList.map(() => []);
    const projectPointOnSeg = (px, py, s) => {
      const vx = s.x2 - s.x1;
      const vy = s.y2 - s.y1;
      const lenSq = vx * vx + vy * vy;
      if (lenSq < 1e-6) return null;
      const t = ((px - s.x1) * vx + (py - s.y1) * vy) / lenSq;
      if (t < -0.02 || t > 1.02) return null;
      const cx = s.x1 + Math.max(0, Math.min(1, t)) * vx;
      const cy = s.y1 + Math.max(0, Math.min(1, t)) * vy;
      if ((px - cx) ** 2 + (py - cy) ** 2 > 16) return null;
      return { t: Math.max(0, Math.min(1, t)), x: cx, y: cy };
    };

    segList.forEach((s, idx) => {
      segSplits[idx].push({ t: 0, v: findOrAddVertex(s.x1, s.y1) });
      segSplits[idx].push({ t: 1, v: findOrAddVertex(s.x2, s.y2) });
      segList.forEach((other, jdx) => {
        if (idx === jdx) return;
        const pStart = projectPointOnSeg(other.x1, other.y1, s);
        if (pStart) segSplits[idx].push({ t: pStart.t, v: findOrAddVertex(pStart.x, pStart.y) });
        const pEnd = projectPointOnSeg(other.x2, other.y2, s);
        if (pEnd) segSplits[idx].push({ t: pEnd.t, v: findOrAddVertex(pEnd.x, pEnd.y) });
      });
    });

    const adj = Array.from({ length: vertices.length }, () => []);
    segSplits.forEach((list) => {
      list.sort((a, b) => a.t - b.t);
      const uniqueVerts = [];
      list.forEach((item) => {
        if (!uniqueVerts.length || uniqueVerts[uniqueVerts.length - 1] !== item.v) {
          uniqueVerts.push(item.v);
        }
      });
      for (let k = 0; k < uniqueVerts.length - 1; k++) {
        const u = uniqueVerts[k];
        const v = uniqueVerts[k + 1];
        const dist = Math.hypot(vertices[v].x - vertices[u].x, vertices[v].y - vertices[u].y);
        if (dist > 4) {
          adj[u].push({ to: v, len: dist });
          adj[v].push({ to: u, len: dist });
        }
      }
    });

    return { vertices, adj };
  }

  function rebuildRouteLengths() {
    routeCumLens = [0];
    let acc = 0;
    for (let i = 1; i < routePts.length; i++) {
      acc += Math.hypot(routePts[i].x - routePts[i - 1].x, routePts[i].y - routePts[i - 1].y);
      routeCumLens.push(acc);
    }
    routeTotalLen = acc;
  }

  function getPointAtRouteDist(dist) {
    if (!routePts.length) return { x: 500, y: 300 };
    if (routePts.length === 1 || dist <= 0) return routePts[0];
    if (dist >= routeTotalLen) return routePts[routePts.length - 1];
    for (let i = 1; i < routeCumLens.length; i++) {
      if (dist <= routeCumLens[i]) {
        const segStart = routeCumLens[i - 1];
        const segLen = Math.max(1e-6, routeCumLens[i] - segStart);
        const frac = (dist - segStart) / segLen;
        return {
          x: routePts[i - 1].x + (routePts[i].x - routePts[i - 1].x) * frac,
          y: routePts[i - 1].y + (routePts[i].y - routePts[i - 1].y) * frac
        };
      }
    }
    return routePts[routePts.length - 1];
  }

  /** Build an SVG polyline slice strictly along the active route centerline. */
  function buildRouteSlicePath(startDist, endDist) {
    if (routePts.length < 2) return null;
    const s = Math.max(0, startDist);
    const e = Math.min(routeTotalLen, endDist);
    if (e - s < 0.5) return null;

    const startPt = getPointAtRouteDist(s);
    const endPt = getPointAtRouteDist(e);
    const parts = [`M ${startPt.x.toFixed(1)} ${startPt.y.toFixed(1)}`];

    for (let i = 1; i < routePts.length; i++) {
      const cum = routeCumLens[i];
      if (cum > s + 0.25 && cum < e - 0.25) {
        parts.push(`L ${routePts[i].x.toFixed(1)} ${routePts[i].y.toFixed(1)}`);
      }
    }

    parts.push(`L ${endPt.x.toFixed(1)} ${endPt.y.toFixed(1)}`);
    return { d: parts.join(' '), endPt };
  }

  function pickSpanScenario(cfg, prevScenario) {
    const list = cfg.scenarios;
    if (!list || !list.length) return { origin: '출발지', dest: '도착지', startMsg: '경로 추적', endMsg: '도착 알람' };
    let chosen = list[Math.floor(Math.random() * list.length)];
    if (list.length > 1 && prevScenario && chosen === prevScenario) {
      chosen = list[(list.indexOf(chosen) + 1) % list.length];
    }
    return chosen;
  }

  function advanceOverlaySpans(spans, cursor, targetLen, cfg, groupEl) {
    if (!groupEl) return cursor;
    let cur = cursor;
    while (cur < targetLen) {
      const start = cur;
      const len = randBetween(cfg.minLen, cfg.maxLen);
      const end = start + len;
      const seq = nextSpanSeq++;
      const prevMeta = spans.length ? spans[spans.length - 1].meta : null;
      const meta = pickSpanScenario(cfg, prevMeta);

      const pathEl = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      pathEl.setAttribute('class', cfg.className);
      pathEl.style.display = 'none';
      groupEl.appendChild(pathEl);

      spans.push({ id: seq, start, end, meta, el: pathEl, drawnEnd: -1 });
      alarmEvents.push(
        {
          id: `${cfg.type}-s-${seq}`,
          triggerDist: start,
          type: cfg.type,
          tag: cfg.tag,
          kind: '추적',
          isDispatch: false,
          msg: meta.startMsg
        },
        {
          id: `${cfg.type}-e-${seq}`,
          triggerDist: start + len * 0.72,
          type: cfg.type,
          tag: cfg.tag,
          kind: '알람',
          isDispatch: true,
          msg: meta.endMsg
        }
      );

      cur = end + randBetween(cfg.minGap, cfg.maxGap);
    }
    return cur;
  }

  function ensureOverlaySpansUpTo(targetLen) {
    nextHumanCursor = advanceOverlaySpans(
      humanSpans,
      nextHumanCursor,
      targetLen,
      HUMAN_SPAN_CONFIG,
      odHumanRoutesGroup
    );
    nextCargoCursor = advanceOverlaySpans(
      cargoSpans,
      nextCargoCursor,
      targetLen,
      CARGO_SPAN_CONFIG,
      odCargoRoutesGroup
    );
    alarmEvents.sort((a, b) => a.triggerDist - b.triggerDist);
  }

  function syncOverlaySpanGroup(spans, maxVisibleDist) {
    for (let i = 0; i < spans.length; i++) {
      const span = spans[i];
      if (span.start >= maxVisibleDist - 0.5) {
        if (span.drawnEnd !== -1) {
          span.el.style.display = 'none';
          span.drawnEnd = -1;
        }
        continue;
      }
      const visEnd = Math.min(span.end, maxVisibleDist);
      if (Math.abs(span.drawnEnd - visEnd) < 0.25) continue;
      const slice = buildRouteSlicePath(span.start, visEnd);
      if (slice) {
        span.el.setAttribute('d', slice.d);
        span.el.style.display = '';
        span.drawnEnd = visEnd;
      }
    }
  }

  function evaluateSpanBadgeState(spans, dist, labels) {
    for (let i = 0; i < spans.length; i++) {
      const span = spans[i];
      if (dist >= span.start && dist <= span.end) {
        const progress = (dist - span.start) / Math.max(1, span.end - span.start);
        return progress >= 0.72
          ? { active: true, alert: true, text: labels.alert }
          : { active: true, alert: false, text: labels.active };
      }
      if (dist > span.end && dist <= span.end + 42) {
        return { active: true, alert: true, text: labels.alert };
      }
    }
    return { active: false, alert: false, text: labels.idle };
  }

  function applyContextBadge(badgeEl, textEl, state) {
    if (!badgeEl || !textEl) return;
    badgeEl.classList.toggle('is-active', Boolean(state.active));
    badgeEl.classList.toggle('is-alert', Boolean(state.alert));
    textEl.textContent = state.text;
  }

  function syncOdTrackerRow(rowEl, originEl, destEl, spans, dist, fallback) {
    if (!rowEl || !originEl || !destEl) return;
    let targetSpan = null;
    let isActive = false;

    for (let i = 0; i < spans.length; i++) {
      const s = spans[i];
      if (dist >= s.start && dist <= s.end + 42) {
        targetSpan = s;
        isActive = true;
        break;
      }
      if (s.start <= dist) targetSpan = s;
    }

    rowEl.classList.toggle('is-active', isActive);
    if (targetSpan && targetSpan.meta) {
      originEl.textContent = targetSpan.meta.origin;
      destEl.textContent = targetSpan.meta.dest;
    } else {
      originEl.textContent = fallback.origin;
      destEl.textContent = fallback.dest;
    }
  }

  function createAlarmLogItemEl(ev, isNew) {
    const li = document.createElement('li');
    li.className = `od-hud-log-item is-${ev.type}${ev.isDispatch ? ' is-dispatch' : ''}${isNew ? ' is-new' : ''}`;

    const body = document.createElement('div');
    body.className = 'od-hud-log-body';

    const tagEl = document.createElement('span');
    tagEl.className = 'od-hud-log-tag';
    tagEl.textContent = `[${ev.tag}]`;

    const msgEl = document.createElement('span');
    msgEl.className = 'od-hud-log-msg';
    msgEl.textContent = ev.msg;

    const kindEl = document.createElement('span');
    kindEl.className = 'od-hud-log-kind';
    kindEl.textContent = ev.kind;

    body.appendChild(tagEl);
    body.appendChild(msgEl);
    li.appendChild(body);
    li.appendChild(kindEl);
    return li;
  }

  function syncAlarmLog(dist) {
    if (!odAlertLogList) return;
    if (scrollP >= OD_MOTION_CONFIG.autoCruiseStartScrollP && !phase2MilestoneAdded && phase1Len > 0) {
      phase2MilestoneAdded = true;
      alarmEvents.push({
        id: 'route-follow-cam',
        triggerDist: Math.max(28, phase1Len),
        type: 'route',
        tag: '차량',
        kind: '연속',
        isDispatch: false,
        msg: '연속 주행 모드 · 전방 도착지 실시간 예측'
      });
      alarmEvents.sort((a, b) => a.triggerDist - b.triggerDist);
    }

    const triggered = [];
    for (let i = 0; i < alarmEvents.length; i++) {
      if (alarmEvents[i].triggerDist <= dist) {
        triggered.push(alarmEvents[i]);
      } else {
        break;
      }
    }

    const recent = triggered.slice(-4).reverse();
    const topId = recent.length ? recent[0].id : '';
    const logKey = `${triggered.length}:${topId}`;
    if (logKey === lastLogKey) return;

    const isFreshAlarm = Boolean(topId && topId !== lastTopAlarmId && lastLogKey !== '');
    lastLogKey = logKey;
    lastTopAlarmId = topId;

    if (odAlertCount) {
      odAlertCount.textContent = triggered.length ? `알람 ${triggered.length}건` : '알람 대기 중';
    }

    odAlertLogList.innerHTML = '';
    for (let i = 0; i < recent.length; i++) {
      odAlertLogList.appendChild(createAlarmLogItemEl(recent[i], isFreshAlarm && i === 0));
    }
  }

  function updateContextHud(dist) {
    const isFollow = isStageVisible && scrollP >= OD_MOTION_CONFIG.autoCruiseStartScrollP;
    const routeState = isFollow
      ? { active: true, alert: false, text: '연속 주행 이해' }
      : dist > 32
        ? { active: true, alert: false, text: '경로 추적 중' }
        : { active: true, alert: false, text: '출발 대기' };

    const humanState = evaluateSpanBadgeState(humanSpans, dist, {
      active: '동승·이동 인지 중',
      alert: '목적지 하차 알림',
      idle: '동선 대기'
    });

    const cargoState = evaluateSpanBadgeState(cargoSpans, dist, {
      active: '트렁크 적재 인지 중',
      alert: '하차·망각 방지 알림',
      idle: '적재 대기'
    });

    const activeHumanOrigin = odHumanOrigin ? odHumanOrigin.textContent : '';
    const hudKey = `${routeState.text}|${humanState.active}:${humanState.alert}:${humanState.text}|${cargoState.active}:${cargoState.alert}:${cargoState.text}|${Math.floor(dist / 6)}|${activeHumanOrigin}`;
    if (hudKey !== lastHudKey) {
      lastHudKey = hudKey;
      applyContextBadge(odBadgeRoute, odBadgeRouteText, routeState);
      applyContextBadge(odBadgeHuman, odBadgeHumanText, humanState);
      applyContextBadge(odBadgeCargo, odBadgeCargoText, cargoState);
      syncOdTrackerRow(odTrackHuman, odHumanOrigin, odHumanDest, humanSpans, dist, { origin: '출발지 탐색', dest: '도착지 예측' });
      syncOdTrackerRow(odTrackCargo, odCargoOrigin, odCargoDest, cargoSpans, dist, { origin: '적재 스캔', dest: '하차지 대기' });
    }

    syncAlarmLog(dist);
  }

  function renderBlueRouteAtDist(dist) {
    if (!blueRouteEl || !blueRouteGlowEl || routePts.length < 2) return { x: 500, y: 300 };
    const clampedDist = Math.max(24, Math.min(routeTotalLen, dist));
    const slice = buildRouteSlicePath(0, clampedDist);
    if (!slice) return routePts[0];

    blueRouteEl.setAttribute('d', slice.d);
    blueRouteGlowEl.setAttribute('d', slice.d);
    syncOverlaySpanGroup(humanSpans, clampedDist);
    syncOverlaySpanGroup(cargoSpans, clampedDist);
    updateContextHud(clampedDist);
    return slice.endPt;
  }

  function ensureTilesAroundPoint(x, y) {
    const c = svgToCity(x, y);
    const tx = Math.floor((c.u + TILE_W * 0.5) / TILE_W);
    const ty = Math.floor((c.v + TILE_H * 0.5) / TILE_H);
    for (let dx = -1; dx <= 1; dx++) {
      for (let dy = -1; dy <= 1; dy++) {
        ensureTileBSP(tx + dx, ty + dy);
      }
    }
  }

  function extendInfiniteRoadAhead() {
    if (routePts.length < 2) return false;
    const last = routePts[routePts.length - 1];
    ensureTilesAroundPoint(last.x, last.y);

    const { vertices, adj } = buildRoadGraph(segments);

    let curV = -1;
    let bestD = Infinity;
    for (let i = 0; i < vertices.length; i++) {
      const d = (vertices[i].x - last.x) ** 2 + (vertices[i].y - last.y) ** 2;
      if (d < bestD && adj[i].length > 0) {
        bestD = d;
        curV = i;
      }
    }
    if (curV === -1) return false;

    const recentSet = new Set();
    for (let k = Math.max(0, routePts.length - 18); k < routePts.length; k++) {
      recentSet.add(`${Math.round(routePts[k].x / 10)},${Math.round(routePts[k].y / 10)}`);
    }

    const desiredAngle = urbanAngle - 0.35;
    const desCos = Math.cos(desiredAngle);
    const desSin = Math.sin(desiredAngle);

    let prevPt = routePts[routePts.length - 2];
    let stepped = false;

    for (let hop = 0; hop < 5; hop++) {
      const edges = adj[curV];
      if (!edges || !edges.length) break;

      const curPt = vertices[curV];
      const inDx = curPt.x - prevPt.x;
      const inDy = curPt.y - prevPt.y;
      const inLen = Math.max(1e-6, Math.hypot(inDx, inDy));

      let chosen = null;
      let bestScore = -Infinity;

      edges.forEach((edge) => {
        const nxt = vertices[edge.to];
        const key = `${Math.round(nxt.x / 10)},${Math.round(nxt.y / 10)}`;
        const edx = nxt.x - curPt.x;
        const edy = nxt.y - curPt.y;
        const eLen = Math.max(1e-6, Math.hypot(edx, edy));

        const dotIn = (inDx * edx + inDy * edy) / (inLen * eLen);
        if (dotIn < -0.75) return;

        const forwardAlign = (edx * desCos + edy * desSin) / eLen;
        const isTurn = Math.abs(dotIn) < 0.5;
        let score = forwardAlign * 2.1 + (isTurn ? 0.42 : 0) + randBetween(-0.22, 0.22);
        if (recentSet.has(key)) score -= 5.0;

        if (score > bestScore) {
          bestScore = score;
          chosen = edge.to;
        }
      });

      if (chosen === null) break;
      prevPt = curPt;
      curV = chosen;
      const nextPt = vertices[curV];
      routePts.push({ x: nextPt.x, y: nextPt.y });
      recentSet.add(`${Math.round(nextPt.x / 10)},${Math.round(nextPt.y / 10)}`);
      stepped = true;
    }

    if (stepped) {
      rebuildRouteLengths();
      ensureOverlaySpansUpTo(routeTotalLen);
    }

    if (segments.length > 180) {
      const camCenterX = VIEW_W * 0.5 - camX;
      const camCenterY = VIEW_H * 0.5 - camY;
      const kept = [];
      for (let i = 0; i < segments.length; i++) {
        const s = segments[i];
        const mx = (s.x1 + s.x2) * 0.5;
        const my = (s.y1 + s.y2) * 0.5;
        if ((mx - camCenterX) ** 2 + (my - camCenterY) ** 2 > 2200 * 2200 && i < segments.length - 80) {
          if (s.el) s.el.remove();
        } else {
          kept.push(s);
        }
      }
      segments = kept;
    }

    return stepped;
  }

  function createSvgCamLayer(svgEl, worldId, groupId) {
    if (!svgEl) return { world: null, group: null };
    svgEl.innerHTML = '';
    const world = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    world.setAttribute('id', worldId);
    const group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    group.setAttribute('id', groupId);
    world.appendChild(group);
    svgEl.appendChild(world);
    return { world, group };
  }

  function applyCameraTransform(tx, ty) {
    const val = `translate(${tx.toFixed(1)}, ${ty.toFixed(1)})`;
    [odCameraWorld, odHumanCamWorld, odCargoCamWorld].forEach((world) => {
      if (world) world.setAttribute('transform', val);
    });
  }

  function generateStraightLineMap() {
    if (!odStraightMapSvg) return;

    const baseLayer = createSvgCamLayer(odStraightMapSvg, 'odCameraWorld', 'odGreyRoadsGroup');
    odCameraWorld = baseLayer.world;
    odGreyRoadsGroup = baseLayer.group;

    const humanLayer = createSvgCamLayer(odHumanMapSvg, 'odHumanCamWorld', 'odHumanRoutesGroup');
    odHumanCamWorld = humanLayer.world;
    odHumanRoutesGroup = humanLayer.group;

    const cargoLayer = createSvgCamLayer(odCargoMapSvg, 'odCargoCamWorld', 'odCargoRoutesGroup');
    odCargoCamWorld = cargoLayer.world;
    odCargoRoutesGroup = cargoLayer.group;

    segments = [];
    loadedTiles = new Set();
    borderKeys = new Set();
    humanSpans = [];
    cargoSpans = [];
    nextSpanSeq = 1;
    phase2MilestoneAdded = false;
    lastHudKey = '';
    lastLogKey = '';
    lastTopAlarmId = '';
    alarmEvents = [
      {
        id: 'route-boot',
        triggerDist: 24,
        type: 'route',
        tag: '차량',
        kind: '추적',
        isDispatch: false,
        msg: '출발지 스캔 · 사람/물건 추적 개시'
      }
    ];
    nextHumanCursor = randBetween(HUMAN_SPAN_CONFIG.initialOffsetMin, HUMAN_SPAN_CONFIG.initialOffsetMax);
    nextCargoCursor = randBetween(CARGO_SPAN_CONFIG.initialOffsetMin, CARGO_SPAN_CONFIG.initialOffsetMax);

    urbanAngle = randBetween(-0.12, 0.12);

    ensureTileBSP(0, 0);
    ensureTileBSP(1, 0);
    ensureTileBSP(0, -1);
    ensureTileBSP(1, -1);

    const { vertices, adj } = buildRoadGraph(segments);

    const visibleVerts = [];
    for (let i = 0; i < vertices.length; i++) {
      const { x, y } = vertices[i];
      if (x >= 115 && x <= 885 && y >= 85 && y <= 515 && adj[i].length > 0) {
        visibleVerts.push(i);
      }
    }
    const pool = visibleVerts.length >= 2 ? visibleVerts : vertices.map((_, i) => i).filter((i) => adj[i].length > 0);
    if (pool.length < 2) return;

    let startV = pool[0];
    let endV = pool[pool.length - 1];
    let minBL = Infinity;
    let maxTR = -Infinity;
    pool.forEach((vi) => {
      const { x, y } = vertices[vi];
      const diagScore = x + (VIEW_H - y);
      if (diagScore < minBL) {
        minBL = diagScore;
        startV = vi;
      }
      if (diagScore > maxTR) {
        maxTR = diagScore;
        endV = vi;
      }
    });

    const distArr = new Array(vertices.length).fill(Infinity);
    const prevArr = new Array(vertices.length).fill(-1);
    const visited = new Array(vertices.length).fill(false);
    distArr[startV] = 0;

    for (let step = 0; step < vertices.length; step++) {
      let u = -1;
      let best = Infinity;
      for (let i = 0; i < vertices.length; i++) {
        if (!visited[i] && distArr[i] < best) {
          best = distArr[i];
          u = i;
        }
      }
      if (u === -1 || u === endV) break;
      visited[u] = true;

      const pIdx = prevArr[u];
      adj[u].forEach((edge) => {
        if (visited[edge.to]) return;
        let weight = edge.len;
        if (pIdx !== -1) {
          const ax = vertices[u].x - vertices[pIdx].x;
          const ay = vertices[u].y - vertices[pIdx].y;
          const bx = vertices[edge.to].x - vertices[u].x;
          const by = vertices[edge.to].y - vertices[u].y;
          const dot = (ax * bx + ay * by) / Math.max(1e-6, Math.hypot(ax, ay) * Math.hypot(bx, by));
          weight *= Math.abs(dot) < 0.7 ? 0.78 : 1.08;
        }
        if (distArr[u] + weight < distArr[edge.to]) {
          distArr[edge.to] = distArr[u] + weight;
          prevArr[edge.to] = u;
        }
      });
    }

    const pathIndices = [];
    let cur = endV;
    while (cur !== -1) {
      pathIndices.push(cur);
      if (cur === startV) break;
      cur = prevArr[cur];
    }
    pathIndices.reverse();
    if (pathIndices.length < 2) return;

    routePts = pathIndices.map((vi) => ({ x: vertices[vi].x, y: vertices[vi].y }));
    rebuildRouteLengths();
    phase1Len = routeTotalLen;

    const glowPath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    glowPath.setAttribute('id', 'odBlueRouteGlow');
    glowPath.setAttribute('class', 'od-blue-route-glow');
    odCameraWorld.appendChild(glowPath);

    const bluePath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    bluePath.setAttribute('id', 'odBlueRoutePath');
    bluePath.setAttribute('class', 'od-blue-route');
    odCameraWorld.appendChild(bluePath);

    blueRouteGlowEl = glowPath;
    blueRouteEl = bluePath;
    ensureOverlaySpansUpTo(routeTotalLen);
    syncOdTrackHeight();
    blueDist = Math.min(phase1Len, 26);
    renderBlueRouteAtDist(blueDist);
  }

  function getOdTargetUnlockDist() {
    if (OD_MOTION_CONFIG.unlockTravelDist > 0) {
      return Math.max(40, OD_MOTION_CONFIG.unlockTravelDist);
    }
    return Math.max(40, phase1Len * Math.max(0.1, OD_MOTION_CONFIG.unlockRouteMultiplier));
  }

  function getOdSvgPerScrollPx() {
    return OD_MOTION_CONFIG.baseSvgPerScrollPx * Math.max(0.02, OD_MOTION_CONFIG.scrollLineSpeedScale);
  }

  function syncOdTrackHeight() {
    if (!odScrollyTrack || !phase1Len) return;
    const stickyH = odStickyStage ? odStickyStage.offsetHeight : 560;
    const targetDist = getOdTargetUnlockDist();
    const svgPerPx = getOdSvgPerScrollPx();
    const scrollSpanPx = Math.max(320, (targetDist - 24) / svgPerPx);
    const cruiseFrac = Math.max(0.1, OD_MOTION_CONFIG.autoCruiseStartScrollP);
    const totalScrollablePx = scrollSpanPx / cruiseFrac;
    odScrollyTrack.style.minHeight = `${Math.round(stickyH + totalScrollablePx)}px`;
  }

  function getScrollRouteDist() {
    const targetDist = getOdTargetUnlockDist();
    const progressRatio = clamp01(scrollP / Math.max(0.05, OD_MOTION_CONFIG.autoCruiseStartScrollP));
    return 24 + (targetDist - 24) * progressRatio;
  }

  function ensureRouteAheadForDist(targetDist) {
    let extGuard = 0;
    while (targetDist + 560 >= routeTotalLen && extGuard < 6) {
      if (!extendInfiniteRoadAhead()) break;
      extGuard++;
    }
  }

  function computeTargetCam(head, isAutoCruise) {
    if (!head) return { x: 0, y: 0 };
    const startP = OD_MOTION_CONFIG.camFollowStartScrollP;
    const scrollFollowT = clamp01((scrollP - startP) / 0.18);
    const guardXStart = VIEW_W * Math.max(0.1, OD_MOTION_CONFIG.panelGuardXRatio - 0.10);
    const guardXRange = Math.max(1, VIEW_W * 0.10);
    const xGuardT = clamp01((head.x - guardXStart) / guardXRange);

    const guardYBandTop = VIEW_H * OD_MOTION_CONFIG.panelGuardYRatio;
    const guardYRange = Math.max(1, VIEW_H * 0.10);
    const yGuardT = clamp01((guardYBandTop + guardYRange - head.y) / guardYRange);

    const followBlend = isAutoCruise ? 1 : Math.max(scrollFollowT, xGuardT, yGuardT);
    return {
      x: (OD_MOTION_CONFIG.camFocusX - head.x) * followBlend,
      y: (OD_MOTION_CONFIG.camFocusY - head.y) * followBlend
    };
  }

  function stepFollowCam(ts) {
    if (!lastFrameTs) lastFrameTs = ts;
    const dt = Math.min(0.05, Math.max(0.001, (ts - lastFrameTs) / 1000));
    lastFrameTs = ts;

    const isAutoCruise = isStageVisible && scrollP >= OD_MOTION_CONFIG.autoCruiseStartScrollP;
    if (isAutoCruise) {
      blueDist += OD_MOTION_CONFIG.autoCruiseSpeed * dt;
    } else {
      blueDist = getScrollRouteDist();
    }

    ensureRouteAheadForDist(blueDist);
    const head = renderBlueRouteAtDist(blueDist);
    const targetCam = computeTargetCam(head, isAutoCruise);

    const damp = 1 - Math.exp(-OD_MOTION_CONFIG.camFollowSmoothness * dt);
    camX += (targetCam.x - camX) * damp;
    camY += (targetCam.y - camY) * damp;
    applyCameraTransform(camX, camY);

    const camSettledAtTarget =
      Math.abs(targetCam.x - camX) < 0.3 && Math.abs(targetCam.y - camY) < 0.3;
    if (isAutoCruise || (!camSettledAtTarget && isStageVisible)) {
      animRafId = requestAnimationFrame(stepFollowCam);
    } else {
      if (camSettledAtTarget) {
        camX = targetCam.x;
        camY = targetCam.y;
        applyCameraTransform(camX, camY);
      }
      animRafId = null;
      lastFrameTs = 0;
    }
  }

  function ensureAnimRunning() {
    if (!animRafId) {
      lastFrameTs = 0;
      animRafId = requestAnimationFrame(stepFollowCam);
    }
  }

  function updatePanelScrollMotion() {
    if (!odAppHudPanel) return;

    const vh = window.innerHeight || 900;
    const coreRect = transportCoreEl ? transportCoreEl.getBoundingClientRect() : null;
    const nextSectionRect = archSectionEl ? archSectionEl.getBoundingClientRect() : null;

    // Fade in smoothly as #transport-core enters the user's screen (fixed on desktop)
    const enterP = coreRect
      ? clamp01((vh * 0.56 - coreRect.top) / (vh * 0.24))
      : 1;

    // Blur out cleanly right before transitioning to the next screen (#architecture)
    const exitP = nextSectionRect
      ? clamp01((vh * 0.82 - nextSectionRect.top) / (vh * 0.24))
      : 0;

    const rawVis = window.innerWidth <= 1024 ? 1 - exitP : enterP * (1 - exitP);
    const vis = rawVis * rawVis * (3 - 2 * rawVis);

    odAppHudPanel.style.setProperty('--hud-scale', (0.95 + 0.05 * vis).toFixed(3));
    odAppHudPanel.style.setProperty('--hud-blur', `${((1 - vis) * 18).toFixed(1)}px`);
    odAppHudPanel.style.setProperty('--hud-opacity', vis.toFixed(3));
    odAppHudPanel.classList.toggle('is-exited', vis <= 0.02);
  }

  function updateTiltedGridStage() {
    if (!odScrollyTrack || !odGridPlane) return;

    const trackRect = odScrollyTrack.getBoundingClientRect();
    isStageVisible = trackRect.top < window.innerHeight * 0.88 && trackRect.bottom > 80;
    if (odGridStage) odGridStage.classList.toggle('is-visible', isStageVisible);
    odGridPlane.classList.toggle('is-visible', isStageVisible);

    const stickyTop = odStickyStage ? parseFloat(getComputedStyle(odStickyStage).top) || 88 : 88;
    const scrollableDist = Math.max(1, odScrollyTrack.offsetHeight - (odStickyStage ? odStickyStage.offsetHeight : 420));
    scrollP = clamp01((stickyTop - trackRect.top) / scrollableDist);

    let targetCam = { x: camX, y: camY };
    if (scrollP < OD_MOTION_CONFIG.autoCruiseStartScrollP) {
      blueDist = getScrollRouteDist();
      ensureRouteAheadForDist(blueDist);
      const head = renderBlueRouteAtDist(blueDist);
      targetCam = computeTargetCam(head, false);
    }

    updatePanelScrollMotion();

    if (
      (isStageVisible && scrollP >= OD_MOTION_CONFIG.autoCruiseStartScrollP) ||
      (isStageVisible && (Math.abs(targetCam.x - camX) > 0.3 || Math.abs(targetCam.y - camY) > 0.3))
    ) {
      ensureAnimRunning();
    }
  }

  generateStraightLineMap();
  window.addEventListener('scroll', updateTiltedGridStage, { passive: true });
  window.addEventListener('resize', () => {
    syncOdTrackHeight();
    updateTiltedGridStage();
  });
  window.addEventListener('load', () => {
    syncOdTrackHeight();
    updateTiltedGridStage();
  });
  updateTiltedGridStage();

  /* ========================================================================
     5. Visualized 4-Layer Architecture (Scroll-Stacked 3D Layers + Pull-Out)
     ======================================================================== */
  const ARCH_LAYERS = [
    {
      step: 'LAYER 01',
      title: '차량 신호 수집',
      module: 'core/signal · PleosSignalProvider',
      chips: ['트렁크·2열 도어', '기어 P/D/R', '실내온도 HVAC', '내비 POI']
    },
    {
      step: 'LAYER 02',
      title: '이동 세션 기억',
      module: 'core/engine · TripSession',
      chips: ['LOADING', 'DRIVING', 'WAYPOINT', 'ARRIVED (P)']
    },
    {
      step: 'LAYER 03',
      title: '안전 · 정밀 판단',
      module: 'SafetyPolicyGuard · 8 Plugins',
      chips: ['경유지 무시', '중복 P단 차단', '주행 중 팝업 락', '8종 플러그인']
    },
    {
      step: 'LAYER 04',
      title: '안내 & 물리 제어',
      module: 'core/actuator · VehicleController',
      chips: ['헤드업 카드', '한국어 TTS', '전동 트렁크', '급속 냉방 · 창문']
    }
  ];

  const ARCH_SCENARIOS = {
    mart: [
      { inText: '트렁크 열림→닫힘 + 마트 GPS', outText: '표준 이벤트 TrunkLoaded(POI=MART)' },
      { inText: '단발성 도어·내비 신호 누적', outText: '출발~도착 TripSession 불변 상태 저장' },
      { inText: '목적지 도착 + 기어 P단 확인', outText: '중간정차 제외 → 하차 알림 1회 승인' },
      { inText: '승인된 하차 케어 액션', outText: '헤드업 카드 + TTS + 트렁크 원터치 열기' }
    ],
    waypoint: [
      { inText: '카페 경유 P단 + 실내온도 28℃', outText: 'GearChanged(P) + TempUpdated(28℃)' },
      { inText: '최종 목적지(집) 아직 주행 중', outText: '세션 상태 전이: WAYPOINT_STOP' },
      { inText: '중간정차 가드 + 변질 위험 평가', outText: '하차 알림 보류 · 냉방 제안만 승인' },
      { inText: '온도 보호 우선 액션 전달', outText: '실내 급속 냉방 가동 (20.0℃)' }
    ],
    avp: [
      { inText: '하차 존 도착 + AVP 자율주차 대기', outText: 'AutonomousValetDropoff 신호 수집' },
      { inText: '트렁크 적재물 미회수 세션 유지', outText: '하차 존 미수거 위험 컨텍스트 결합' },
      { inText: '자율발렛 출발 전 안전 가드 발동', outText: 'AVP 자동 출발 일시 홀드 결정' },
      { inText: '출발 홀드 + 트렁크 개방 명령', outText: '자율주차 보류 + 전동 트렁크 자동 개방' }
    ]
  };

  const archScrollyTrack = document.getElementById('archScrollyTrack');
  const archStickyStage = document.getElementById('archStickyStage');
  const archLayerPile = document.getElementById('archLayerPile');
  const archFieldSvg = document.getElementById('archFieldSvg');
  const archSlabs = Array.from(document.querySelectorAll('.arch-slab'));
  const archShowcaseCard = document.getElementById('archShowcaseCard');
  const archCardStep = document.getElementById('archCardStep');
  const archCardModule = document.getElementById('archCardModule');
  const archCardTitle = document.getElementById('archCardTitle');
  const archCardChips = document.getElementById('archCardChips');
  const archCardIn = document.getElementById('archCardIn');
  const archCardOut = document.getElementById('archCardOut');
  const archStackHint = document.getElementById('archStackHint');
  const archExplodeBtn = document.getElementById('archExplodeBtn');
  const archPresetBtns = Array.from(document.querySelectorAll('.arch-preset-btn'));

  let activeScenario = 'mart';
  let isExploded = true;
  let manualLayer = null;
  let lastRenderedCardKey = '';
  let lastScrollBeat = -1;
  let currentHighlightedLayer = 0;
  let lockedArchStep = null;
  let programmaticArchScrollUntil = 0;
  const slabOpacities = [0, 0, 0, 0];

  const BASIS_STEP = 0.3;
  // 14 white electric-field strands clustered densely around card center
  const FIELD_STRANDS = [
    // Inner tight core (2)
    { du: -0.025, dv: -0.02, width: 2.0, alpha: 0.92, flowDelay: 0.0 },
    { du: 0.028, dv: 0.022, width: 1.9, alpha: 0.88, flowDelay: -0.65 },
    // Mid ring (5)
    { du: -0.085, dv: -0.045, width: 1.35, alpha: 0.72, flowDelay: -0.25 },
    { du: 0.088, dv: -0.04, width: 1.35, alpha: 0.72, flowDelay: -0.9 },
    { du: 0.0, dv: -0.095, width: 1.3, alpha: 0.68, flowDelay: -1.35 },
    { du: -0.075, dv: 0.075, width: 1.35, alpha: 0.72, flowDelay: -0.5 },
    { du: 0.082, dv: 0.07, width: 1.35, alpha: 0.72, flowDelay: -1.1 },
    // Outer central-zone ring (7)
    { du: -0.155, dv: -0.075, width: 0.95, alpha: 0.48, flowDelay: -0.4 },
    { du: 0.155, dv: -0.072, width: 0.95, alpha: 0.48, flowDelay: -1.2 },
    { du: -0.165, dv: 0.02, width: 0.9, alpha: 0.44, flowDelay: -0.8 },
    { du: 0.165, dv: 0.022, width: 0.9, alpha: 0.44, flowDelay: -1.55 },
    { du: -0.115, dv: 0.13, width: 0.95, alpha: 0.48, flowDelay: -0.15 },
    { du: 0.0, dv: 0.145, width: 1.0, alpha: 0.52, flowDelay: -1.0 },
    { du: 0.12, dv: 0.128, width: 0.95, alpha: 0.48, flowDelay: -1.75 }
  ];

  const slabBases = archSlabs.map((slab) => ({
    o: slab.querySelector('.basis-o'),
    u: slab.querySelector('.basis-u'),
    v: slab.querySelector('.basis-v')
  }));

  const fieldWirePool = [];
  const fieldTailBotPool = [];
  const fieldTailTopPool = [];
  const fieldNodePool = [];
  let archTailBotGrad = null;
  let archTailTopGrad = null;
  let fieldSyncRaf = null;
  let fieldSyncEndTs = 0;

  function createFieldPath(ns, className, strokeWidth, delaySec) {
    const p = document.createElementNS(ns, 'path');
    p.setAttribute('class', className);
    p.setAttribute('stroke-width', String(strokeWidth));
    if (delaySec !== undefined) {
      p.style.animationDelay = `${delaySec.toFixed(2)}s`;
    }
    p.style.display = 'none';
    return p;
  }

  function initArchFieldSvg() {
    if (!archFieldSvg || fieldWirePool.length) return;
    const ns = 'http://www.w3.org/2000/svg';

    const defs = document.createElementNS(ns, 'defs');
    archTailBotGrad = document.createElementNS(ns, 'linearGradient');
    archTailBotGrad.setAttribute('id', 'archTailBotGrad');
    archTailBotGrad.setAttribute('gradientUnits', 'userSpaceOnUse');
    const botStop0 = document.createElementNS(ns, 'stop');
    botStop0.setAttribute('offset', '0%');
    botStop0.setAttribute('stop-color', '#ffffff');
    botStop0.setAttribute('stop-opacity', '0');
    const botStop1 = document.createElementNS(ns, 'stop');
    botStop1.setAttribute('offset', '100%');
    botStop1.setAttribute('stop-color', '#ffffff');
    botStop1.setAttribute('stop-opacity', '0.95');
    archTailBotGrad.appendChild(botStop0);
    archTailBotGrad.appendChild(botStop1);

    archTailTopGrad = document.createElementNS(ns, 'linearGradient');
    archTailTopGrad.setAttribute('id', 'archTailTopGrad');
    archTailTopGrad.setAttribute('gradientUnits', 'userSpaceOnUse');
    const topStop0 = document.createElementNS(ns, 'stop');
    topStop0.setAttribute('offset', '0%');
    topStop0.setAttribute('stop-color', '#ffffff');
    topStop0.setAttribute('stop-opacity', '0.95');
    const topStop1 = document.createElementNS(ns, 'stop');
    topStop1.setAttribute('offset', '100%');
    topStop1.setAttribute('stop-color', '#ffffff');
    topStop1.setAttribute('stop-opacity', '0');
    archTailTopGrad.appendChild(topStop0);
    archTailTopGrad.appendChild(topStop1);

    defs.appendChild(archTailBotGrad);
    defs.appendChild(archTailTopGrad);
    archFieldSvg.appendChild(defs);

    FIELD_STRANDS.forEach((strand, strandIdx) => {
      const basePath = createFieldPath(ns, 'arch-field-tail', strand.width);
      basePath.setAttribute('stroke', 'url(#archTailBotGrad)');
      const flowPath = createFieldPath(ns, 'arch-field-flow', (strand.width * 1.15).toFixed(2), strand.flowDelay);
      flowPath.setAttribute('stroke', 'url(#archTailBotGrad)');
      archFieldSvg.appendChild(basePath);
      archFieldSvg.appendChild(flowPath);
      fieldTailBotPool.push({ strandIdx, strand, basePath, flowPath });
    });

    for (let pairIdx = 0; pairIdx < 3; pairIdx++) {
      FIELD_STRANDS.forEach((strand, strandIdx) => {
        const basePath = createFieldPath(ns, 'arch-field-line', strand.width);
        const flowPath = createFieldPath(ns, 'arch-field-flow', (strand.width * 1.25).toFixed(2), strand.flowDelay - pairIdx * 0.22);
        archFieldSvg.appendChild(basePath);
        archFieldSvg.appendChild(flowPath);
        fieldWirePool.push({ pairIdx, strandIdx, strand, basePath, flowPath });
      });
    }

    FIELD_STRANDS.forEach((strand, strandIdx) => {
      const basePath = createFieldPath(ns, 'arch-field-tail', strand.width);
      basePath.setAttribute('stroke', 'url(#archTailTopGrad)');
      const flowPath = createFieldPath(ns, 'arch-field-flow', (strand.width * 1.15).toFixed(2), strand.flowDelay - 0.66);
      flowPath.setAttribute('stroke', 'url(#archTailTopGrad)');
      archFieldSvg.appendChild(basePath);
      archFieldSvg.appendChild(flowPath);
      fieldTailTopPool.push({ strandIdx, strand, basePath, flowPath });
    });

    for (let layerIdx = 0; layerIdx < 4; layerIdx++) {
      FIELD_STRANDS.forEach((strand, strandIdx) => {
        const circle = document.createElementNS(ns, 'circle');
        circle.setAttribute('class', 'arch-field-node');
        circle.setAttribute('r', (1.1 + strand.width * 0.65).toFixed(2));
        circle.style.display = 'none';
        archFieldSvg.appendChild(circle);
        fieldNodePool.push({ layerIdx, strandIdx, strand, el: circle });
      });
    }
  }

  function measureCenter(el, svgRect) {
    const r = el.getBoundingClientRect();
    return {
      x: r.left + r.width * 0.5 - svgRect.left,
      y: r.top + r.height * 0.5 - svgRect.top
    };
  }

  function syncArchFieldLines() {
    if (!archFieldSvg || !fieldWirePool.length) return;
    const svgRect = archFieldSvg.getBoundingClientRect();
    if (svgRect.width <= 0 || svgRect.height <= 0) return;

    let botIdx = -1;
    let topIdx = -1;
    const layerCoords = slabBases.map((basis, layerIdx) => {
      if (slabOpacities[layerIdx] <= 0.12 || !basis.o || !basis.u || !basis.v) return null;
      if (botIdx === -1) botIdx = layerIdx;
      topIdx = layerIdx;
      const po = measureCenter(basis.o, svgRect);
      const pu = measureCenter(basis.u, svgRect);
      const pv = measureCenter(basis.v, svgRect);
      const ux = (pu.x - po.x) / BASIS_STEP;
      const uy = (pu.y - po.y) / BASIS_STEP;
      const vx = (pv.x - po.x) / BASIS_STEP;
      const vy = (pv.y - po.y) / BASIS_STEP;
      return FIELD_STRANDS.map((s) => ({
        x: po.x + s.du * ux + s.dv * vx,
        y: po.y + s.du * uy + s.dv * vy
      }));
    });

    const tailLen = 58;
    const botCenter = botIdx >= 0 ? measureCenter(slabBases[botIdx].o, svgRect) : null;
    if (archTailBotGrad && botCenter) {
      archTailBotGrad.setAttribute('x1', '0');
      archTailBotGrad.setAttribute('y1', (botCenter.y + tailLen).toFixed(1));
      archTailBotGrad.setAttribute('x2', '0');
      archTailBotGrad.setAttribute('y2', botCenter.y.toFixed(1));
    }

    fieldTailBotPool.forEach(({ strandIdx, strand, basePath, flowPath }) => {
      if (botIdx < 0 || !layerCoords[botIdx]) {
        basePath.style.display = 'none';
        flowPath.style.display = 'none';
        return;
      }
      const pTop = layerCoords[botIdx][strandIdx];
      const pStart = { x: pTop.x + strand.du * 18, y: pTop.y + tailLen };
      const dy = pTop.y - pStart.y;
      const d = `M ${pStart.x.toFixed(1)} ${pStart.y.toFixed(1)} C ${pStart.x.toFixed(1)} ${(pStart.y + dy * 0.52).toFixed(1)}, ${pTop.x.toFixed(1)} ${(pTop.y - dy * 0.48).toFixed(1)}, ${pTop.x.toFixed(1)} ${pTop.y.toFixed(1)}`;
      const visAlpha = slabOpacities[botIdx];
      basePath.setAttribute('d', d);
      basePath.style.display = '';
      basePath.style.opacity = (strand.alpha * 0.58 * visAlpha).toFixed(2);
      flowPath.setAttribute('d', d);
      flowPath.style.display = '';
      flowPath.style.opacity = (strand.alpha * 0.9 * visAlpha).toFixed(2);
    });

    fieldWirePool.forEach(({ pairIdx, strandIdx, strand, basePath, flowPath }) => {
      const botCoords = layerCoords[pairIdx];
      const topCoords = layerCoords[pairIdx + 1];
      if (!botCoords || !topCoords) {
        basePath.style.display = 'none';
        flowPath.style.display = 'none';
        return;
      }
      const p0 = botCoords[strandIdx];
      const p1 = topCoords[strandIdx];
      const dy = p1.y - p0.y;
      const d = `M ${p0.x.toFixed(1)} ${p0.y.toFixed(1)} C ${p0.x.toFixed(1)} ${(p0.y + dy * 0.48).toFixed(1)}, ${p1.x.toFixed(1)} ${(p1.y - dy * 0.48).toFixed(1)}, ${p1.x.toFixed(1)} ${p1.y.toFixed(1)}`;
      const visAlpha = Math.min(slabOpacities[pairIdx], slabOpacities[pairIdx + 1]);
      const isFocused = pairIdx === currentHighlightedLayer || pairIdx + 1 === currentHighlightedLayer;
      const focusScale = isFocused ? 1.0 : 0.72;

      basePath.setAttribute('d', d);
      basePath.style.display = '';
      basePath.style.opacity = (strand.alpha * 0.55 * visAlpha * focusScale).toFixed(2);

      flowPath.setAttribute('d', d);
      flowPath.style.display = '';
      flowPath.style.opacity = (strand.alpha * 0.95 * visAlpha * focusScale).toFixed(2);
    });

    const topCenter = topIdx >= 0 ? measureCenter(slabBases[topIdx].o, svgRect) : null;
    if (archTailTopGrad && topCenter) {
      archTailTopGrad.setAttribute('x1', '0');
      archTailTopGrad.setAttribute('y1', topCenter.y.toFixed(1));
      archTailTopGrad.setAttribute('x2', '0');
      archTailTopGrad.setAttribute('y2', (topCenter.y - tailLen).toFixed(1));
    }

    fieldTailTopPool.forEach(({ strandIdx, strand, basePath, flowPath }) => {
      if (topIdx < 0 || !layerCoords[topIdx]) {
        basePath.style.display = 'none';
        flowPath.style.display = 'none';
        return;
      }
      const pBot = layerCoords[topIdx][strandIdx];
      const pEnd = { x: pBot.x + strand.du * 18, y: pBot.y - tailLen };
      const dy = pEnd.y - pBot.y;
      const d = `M ${pBot.x.toFixed(1)} ${pBot.y.toFixed(1)} C ${pBot.x.toFixed(1)} ${(pBot.y + dy * 0.48).toFixed(1)}, ${pEnd.x.toFixed(1)} ${(pEnd.y - dy * 0.52).toFixed(1)}, ${pEnd.x.toFixed(1)} ${pEnd.y.toFixed(1)}`;
      const visAlpha = slabOpacities[topIdx];
      basePath.setAttribute('d', d);
      basePath.style.display = '';
      basePath.style.opacity = (strand.alpha * 0.58 * visAlpha).toFixed(2);
      flowPath.setAttribute('d', d);
      flowPath.style.display = '';
      flowPath.style.opacity = (strand.alpha * 0.9 * visAlpha).toFixed(2);
    });

    fieldNodePool.forEach(({ layerIdx, strandIdx, strand, el }) => {
      const coords = layerCoords[layerIdx];
      if (!coords) {
        el.style.display = 'none';
        return;
      }
      el.setAttribute('cx', coords[strandIdx].x.toFixed(1));
      el.setAttribute('cy', coords[strandIdx].y.toFixed(1));
      el.style.display = '';
      el.style.opacity = (strand.alpha * 0.9 * slabOpacities[layerIdx]).toFixed(2);
    });
  }

  function stepArchFieldSync(now) {
    syncArchFieldLines();
    if (now < fieldSyncEndTs) {
      fieldSyncRaf = requestAnimationFrame(stepArchFieldSync);
    } else {
      fieldSyncRaf = null;
    }
  }

  function scheduleFieldSync(durationMs = 520) {
    fieldSyncEndTs = Math.max(fieldSyncEndTs, performance.now() + durationMs);
    syncArchFieldLines();
    if (!fieldSyncRaf && durationMs > 0) {
      fieldSyncRaf = requestAnimationFrame(stepArchFieldSync);
    }
  }

  function smooth01(t) {
    const x = Math.min(1, Math.max(0, t));
    return x * x * (3 - 2 * x);
  }

  function renderArchShowcaseContent(layerIdx) {
    if (!archShowcaseCard) return;
    const key = `${layerIdx}:${activeScenario}`;
    if (key === lastRenderedCardKey) return;
    lastRenderedCardKey = key;

    const layer = ARCH_LAYERS[layerIdx] || ARCH_LAYERS[0];
    const trace = (ARCH_SCENARIOS[activeScenario] || ARCH_SCENARIOS.mart)[layerIdx];

    archShowcaseCard.setAttribute('data-layer', String(layerIdx));
    if (archCardStep) archCardStep.textContent = layer.step;
    if (archCardModule) archCardModule.textContent = layer.module;
    if (archCardTitle) archCardTitle.textContent = layer.title;
    if (archCardChips) {
      archCardChips.innerHTML = '';
      layer.chips.forEach((label) => {
        const span = document.createElement('span');
        span.className = 'arch-chip';
        span.textContent = label;
        archCardChips.appendChild(span);
      });
    }
    if (archCardIn) archCardIn.textContent = trace.inText;
    if (archCardOut) archCardOut.textContent = trace.outText;

    archShowcaseCard.classList.remove('is-revealing');
    void archShowcaseCard.offsetWidth;
    archShowcaseCard.classList.add('is-revealing');
  }

  function applySlabTransform(slab, idx, state) {
    const zStep = isExploded ? 62 : 36;
    const baseZ = idx * zStep;
    const explodeY = isExploded ? (idx - 1.5) * -8 : 0;
    const op = state.opacity ?? 1;
    slabOpacities[idx] = op;

    slab.style.setProperty('--slab-x', `${(state.x || 0).toFixed(1)}px`);
    slab.style.setProperty('--slab-y', `${((state.y || 0) + explodeY).toFixed(1)}px`);
    slab.style.setProperty('--slab-z', `${(baseZ + (state.extraZ || 0)).toFixed(1)}px`);
    slab.style.setProperty('--slab-scale', (state.scale ?? 1).toFixed(3));
    slab.style.setProperty('--slab-opacity', op.toFixed(3));
    slab.classList.toggle('is-stacked', Boolean(state.stacked));
    slab.classList.toggle('is-pulled', Boolean(state.pulled));
  }

  function applyShowcaseTransform(x, scale, blur, opacity, interactive) {
    if (!archShowcaseCard) return;
    archShowcaseCard.classList.toggle('is-interactive', Boolean(interactive));
    archShowcaseCard.style.setProperty('--card-x', `${x.toFixed(1)}px`);
    archShowcaseCard.style.setProperty('--card-scale', scale.toFixed(3));
    archShowcaseCard.style.setProperty('--card-blur', `${blur.toFixed(1)}px`);
    archShowcaseCard.style.setProperty('--card-opacity', opacity.toFixed(3));
  }

  const ARCH_STEP_RATIOS = [0.06, 0.28, 0.50, 0.72, 0.94];

  function getArchMetrics() {
    const trackRect = archScrollyTrack.getBoundingClientRect();
    const stickyTop = archStickyStage ? parseFloat(getComputedStyle(archStickyStage).top) || 164 : 164;
    const scrollableDist = Math.max(1, archScrollyTrack.offsetHeight - (archStickyStage ? archStickyStage.offsetHeight : 520));
    return { trackRect, stickyTop, scrollableDist };
  }

  function getArchStepFromProgress(archP) {
    if (archP >= 0.84) return 4;
    if (archP >= 0.62) return 3;
    if (archP >= 0.40) return 2;
    if (archP >= 0.18) return 1;
    return 0;
  }

  function getStepScrollY(stepIdx) {
    const { trackRect, stickyTop, scrollableDist } = getArchMetrics();
    const ratio = ARCH_STEP_RATIOS[Math.max(0, Math.min(4, stepIdx))];
    return window.scrollY + (trackRect.top - stickyTop) + ratio * scrollableDist;
  }

  function applyArchStepVisuals(stepIdx) {
    const isMobile = window.innerWidth <= 960;
    const isAssembled = isMobile || stepIdx >= 4;
    const useManualMode = isAssembled || manualLayer !== null;
    archLayerPile.classList.toggle('is-interactive', useManualMode);

    if (useManualMode) {
      const selectedIdx = manualLayer !== null ? manualLayer : 3;
      currentHighlightedLayer = selectedIdx;
      renderArchShowcaseContent(selectedIdx);
      applyShowcaseTransform(0, 1, 0, 1, true);

      archSlabs.forEach((slab, idx) => {
        const stacked = isAssembled || idx < stepIdx;
        const isPulled = stacked && idx === selectedIdx;
        const baseOpacity = !stacked ? 0 : isPulled ? 1 : isExploded ? 0.9 : 0.62;
        applySlabTransform(slab, idx, {
          x: isPulled ? 42 : 0,
          extraZ: isPulled ? 8 : 0,
          opacity: baseOpacity,
          stacked,
          pulled: isPulled
        });
      });

      if (archStackHint) {
        archStackHint.textContent = isAssembled
          ? '원하는 레이어를 클릭해 스택에서 꺼내어 확인하세요 · 다시 스크롤하면 다음 섹션으로 이동'
          : '원하는 레이어를 클릭해 스택에서 꺼내어 확인하세요';
      }
      // scheduleFieldSync(480); // 전기장 선 시각화 임시 비활성화
      return;
    }

    const activeIdx = Math.max(0, Math.min(3, stepIdx));
    currentHighlightedLayer = activeIdx;
    renderArchShowcaseContent(activeIdx);
    // Keep right showcase card razor-sharp (zero text blur)
    applyShowcaseTransform(0, 1, 0, 1, false);

    archSlabs.forEach((slab, idx) => {
      if (idx < activeIdx) {
        applySlabTransform(slab, idx, { x: 0, y: 0, opacity: 0.9, stacked: true, pulled: false });
      } else {
        applySlabTransform(slab, idx, {
          x: 170,
          y: -28,
          extraZ: 28,
          scale: 0.94,
          opacity: 0,
          stacked: false,
          pulled: false
        });
      }
    });

    if (archStackHint) {
      archStackHint.textContent = `${ARCH_LAYERS[activeIdx].step} 확인 중 (${activeIdx + 1}/4) · 스크롤을 떼었다 다시 내려 쌓기`;
    }
    // scheduleFieldSync(180); // 전기장 선 시각화 임시 비활성화
  }

  function scrollToArchStep(stepIdx) {
    lockedArchStep = stepIdx;
    lastScrollBeat = stepIdx;
    programmaticArchScrollUntil = performance.now() + 520;
    window.scrollTo({ top: getStepScrollY(stepIdx), behavior: 'smooth' });
    applyArchStepVisuals(stepIdx);
  }

  function updateArchitectureStage() {
    if (!archScrollyTrack || !archLayerPile) return;

    if (window.innerWidth <= 960) {
      lockedArchStep = 4;
      applyArchStepVisuals(4);
      return;
    }

    if (performance.now() < programmaticArchScrollUntil && lockedArchStep !== null) {
      applyArchStepVisuals(lockedArchStep);
      return;
    }

    const { trackRect, stickyTop, scrollableDist } = getArchMetrics();
    const archP = Math.min(1, Math.max(0, (stickyTop - trackRect.top) / scrollableDist));
    const activeStep = getArchStepFromProgress(archP);

    if (activeStep !== lastScrollBeat) {
      lastScrollBeat = activeStep;
      manualLayer = null;
    }

    if (trackRect.top <= stickyTop + 6 && trackRect.top >= stickyTop - scrollableDist - 6) {
      lockedArchStep = activeStep;
    } else {
      lockedArchStep = null;
    }

    applyArchStepVisuals(activeStep);
  }

  let wheelConsumedThisGesture = false;
  let wheelIdleTimer = null;
  let wheelCooldownUntil = 0;
  let wheelDecayedLow = false;

  function armWheelGestureLock(durationMs) {
    wheelConsumedThisGesture = true;
    wheelDecayedLow = false;
    wheelCooldownUntil = performance.now() + durationMs;
  }

  function onArchWheel(e) {
    if (window.innerWidth <= 960 || !archScrollyTrack || !archLayerPile || e.ctrlKey) return;

    const rawDy = e.deltaMode === 1
      ? e.deltaY * 32
      : e.deltaMode === 2
        ? e.deltaY * (window.innerHeight || 900)
        : e.deltaY;
    const absDy = Math.abs(rawDy);
    if (absDy < 2) return;

    const now = performance.now();
    clearTimeout(wheelIdleTimer);
    wheelIdleTimer = setTimeout(() => {
      wheelConsumedThisGesture = false;
      wheelDecayedLow = false;
    }, 190);

    if (wheelConsumedThisGesture) {
      if (absDy < 8) {
        wheelDecayedLow = true;
      } else if (wheelDecayedLow && absDy >= 28 && now >= wheelCooldownUntil) {
        wheelConsumedThisGesture = false;
        wheelDecayedLow = false;
      }
    }

    const dir = rawDy > 0 ? 1 : -1;
    const { trackRect, stickyTop, scrollableDist } = getArchMetrics();
    const minTrackTop = stickyTop - scrollableDist;
    const insideSticky = trackRect.top <= stickyTop + 6 && trackRect.top >= minTrackTop - 6;
    const enteringFromTop = dir > 0 && trackRect.top > stickyTop + 6 && trackRect.top - rawDy <= stickyTop + 20;
    const enteringFromBottom = dir < 0 && trackRect.top < minTrackTop - 6 && trackRect.top - rawDy >= minTrackTop - 20;

    if (!insideSticky && !enteringFromTop && !enteringFromBottom) {
      return;
    }

    if (enteringFromTop) {
      e.preventDefault();
      if (!wheelConsumedThisGesture && now >= wheelCooldownUntil) {
        manualLayer = null;
        scrollToArchStep(0);
        armWheelGestureLock(420);
      }
      return;
    }

    if (enteringFromBottom) {
      e.preventDefault();
      if (!wheelConsumedThisGesture && now >= wheelCooldownUntil) {
        manualLayer = null;
        scrollToArchStep(4);
        armWheelGestureLock(420);
      }
      return;
    }

    const archP = Math.min(1, Math.max(0, (stickyTop - trackRect.top) / scrollableDist));
    const currentStep = lockedArchStep !== null ? lockedArchStep : getArchStepFromProgress(archP);

    if ((currentStep === 4 && dir > 0) || (currentStep === 0 && dir < 0)) {
      if (wheelConsumedThisGesture || now < wheelCooldownUntil) {
        e.preventDefault();
        return;
      }
      lockedArchStep = null;
      return;
    }

    e.preventDefault();
    if (wheelConsumedThisGesture || now < wheelCooldownUntil) {
      return;
    }

    const nextStep = Math.max(0, Math.min(4, currentStep + dir));
    manualLayer = null;
    scrollToArchStep(nextStep);
    armWheelGestureLock(420);
  }

  let archTouchStartY = 0;
  let touchConsumedThisGesture = false;

  function onArchTouchStart(e) {
    if (e.touches.length !== 1) return;
    archTouchStartY = e.touches[0].clientY;
    touchConsumedThisGesture = false;
  }

  function onArchTouchMove(e) {
    if (window.innerWidth <= 960 || !archScrollyTrack || !archLayerPile || e.touches.length !== 1) return;
    const dy = archTouchStartY - e.touches[0].clientY;
    if (Math.abs(dy) < 24) return;

    const { trackRect, stickyTop, scrollableDist } = getArchMetrics();
    const minTrackTop = stickyTop - scrollableDist;
    if (trackRect.top > stickyTop + 6 || trackRect.top < minTrackTop - 6) return;

    const dir = dy > 0 ? 1 : -1;
    const archP = Math.min(1, Math.max(0, (stickyTop - trackRect.top) / scrollableDist));
    const currentStep = lockedArchStep !== null ? lockedArchStep : getArchStepFromProgress(archP);

    if ((currentStep === 4 && dir > 0) || (currentStep === 0 && dir < 0)) {
      if (touchConsumedThisGesture) e.preventDefault();
      return;
    }

    e.preventDefault();
    if (touchConsumedThisGesture) return;
    touchConsumedThisGesture = true;
    manualLayer = null;
    scrollToArchStep(Math.max(0, Math.min(4, currentStep + dir)));
  }

  function onArchTouchEnd() {
    touchConsumedThisGesture = false;
  }

  // initArchFieldSvg(); // 전기장 선 시각화 임시 비활성화

  archSlabs.forEach((slab) => {
    slab.addEventListener('click', () => {
      if (!slab.classList.contains('is-stacked')) return;
      manualLayer = Number(slab.dataset.layer);
      updateArchitectureStage();
    });
  });

  function syncExplodeBtn() {
    if (!archExplodeBtn) return;
    archExplodeBtn.classList.toggle('is-active', isExploded);
    archExplodeBtn.setAttribute('aria-pressed', String(isExploded));
    archExplodeBtn.textContent = isExploded ? '다시 모으기' : '전체 분해';
  }

  if (archExplodeBtn) {
    syncExplodeBtn();
    archExplodeBtn.addEventListener('click', () => {
      isExploded = !isExploded;
      syncExplodeBtn();
      updateArchitectureStage();
    });
  }

  archPresetBtns.forEach((btn) => {
    btn.addEventListener('click', () => {
      activeScenario = btn.dataset.scenario || 'mart';
      archPresetBtns.forEach((b) => b.classList.toggle('is-active', b === btn));
      lastRenderedCardKey = '';
      updateArchitectureStage();
    });
  });

  window.addEventListener('wheel', onArchWheel, { passive: false });
  window.addEventListener('touchstart', onArchTouchStart, { passive: true });
  window.addEventListener('touchmove', onArchTouchMove, { passive: false });
  window.addEventListener('touchend', onArchTouchEnd, { passive: true });
  window.addEventListener('scroll', updateArchitectureStage, { passive: true });
  window.addEventListener('resize', updateArchitectureStage);
  updateArchitectureStage();

  /* ========================================================================
     7. OUR SECRET PLAN: Interactive Context Burst & App Constellation
     ======================================================================== */
  const secretPlanEl = document.getElementById('secret-plan');
  const ciUniverse = document.getElementById('ciUniverse');
  const ciLinkSvg = document.getElementById('ciLinkSvg');
  const ciFxLayer = document.getElementById('ciFxLayer');
  const ciNodeLayer = document.getElementById('ciNodeLayer');
  const ciCoreOrb = document.getElementById('ciCoreOrb');
  const ciOrbKicker = document.getElementById('ciOrbKicker');
  const ciOrbLabel = document.getElementById('ciOrbLabel');
  const ciStatusText = document.getElementById('ciStatusText');
  const ciResetBtn = document.getElementById('ciResetBtn');
  const ciPips = Array.from(document.querySelectorAll('.ci-pip'));

  const CI_CENTER = { x: 50, y: 47 };

  const CI_NODES = [
    // Wave 1: 물리 신호
    {
      id: 'trunk',
      wave: 1,
      cat: '신호',
      label: '🚪 트렁크 개폐',
      angle: -142,
      rx: 23,
      ry: 23,
      insight: '단순한 문 열림이 아니라, 출발지·목적지·경유와 연결될 때 비로소 적재/하차 의도가 됩니다.'
    },
    {
      id: 'gear',
      wave: 1,
      cat: '주행',
      label: '⚙️ 기어 P · D',
      angle: -38,
      rx: 23,
      ry: 23,
      insight: 'P단 전환은 목적지·경유 여부와 결합되어야 오작동 없는 정확한 안내 타이밍을 만듭니다.'
    },
    {
      id: 'temp',
      wave: 1,
      cat: '환경',
      label: '🌡️ 실내 28.5℃',
      angle: 38,
      rx: 23,
      ry: 23,
      insight: '실내 온도는 마트 출발·신선식품 맥락과 이어질 때 급속 냉방 제안이라는 배려로 바뀝니다.'
    },
    {
      id: 'cabin',
      wave: 1,
      cat: '캐빈',
      label: '💺 2열 착석·창문',
      angle: 142,
      rx: 23,
      ry: 23,
      insight: '2열 도어·착석·창문 상태가 기상·주차 맥락과 합쳐져 짐 적재와 하차 점검을 완성합니다.'
    },
    // Wave 2: 시공간 단서
    {
      id: 'mart',
      wave: 2,
      cat: '장소',
      label: '🛒 출발지 마트',
      angle: -96,
      rx: 28,
      ry: 34,
      insight: '출발지 POI가 마트이고 트렁크가 열렸다는 사실이 오늘 짐의 성격을 추론하는 시작점입니다.'
    },
    {
      id: 'evening',
      wave: 2,
      cat: '시간',
      label: '🕒 퇴근 오후 6:20',
      angle: -12,
      rx: 38,
      ry: 30,
      insight: '퇴근 시간과 요일 패턴이 누적되면 상시 보관품과 오늘 새로 실은 물건을 스스로 구분합니다.'
    },
    {
      id: 'cafe',
      wave: 2,
      cat: '경유',
      label: '☕ 경유지 카페',
      angle: 64,
      rx: 30,
      ry: 35,
      insight: '중간 경유지 카페에서의 P단 정차는 사람만 잠시 내리는 순간이므로 짐 알림을 참습니다.'
    },
    {
      id: 'rain',
      wave: 2,
      cat: '기상',
      label: '🌧️ 도착지 비 예보',
      angle: 120,
      rx: 32,
      ry: 35,
      insight: '날씨 맥락이 목적지와 이어지면 창문 닫기 확인과 우산 챙김 안내까지 한 번에 연결됩니다.'
    },
    {
      id: 'home',
      wave: 2,
      cat: '도착',
      label: '🏠 최종 목적지 집',
      angle: -166,
      rx: 38,
      ry: 31,
      insight: '최종 목적지 도착과 트렁크 적재 기억이 만나는 순간, 하차 전 마지막 5초 브리핑이 열립니다.'
    },
    // Wave 3: 의도 & 개인화 기억
    {
      id: 'fresh',
      wave: 3,
      cat: '의도',
      label: '🧊 신선식품 보호',
      angle: -56,
      rx: 35,
      ry: 35,
      insight: '마트 출발 · 트렁크 개폐 · 실내 고온이 이어지며 신선식품 보호 타이머가 작동합니다.'
    },
    {
      id: 'golf',
      wave: 3,
      cat: '습관',
      label: '⛳ 금요일 골프백',
      angle: 16,
      rx: 39,
      ry: 32,
      insight: '반복 적재된 상시 보관품으로 학습된 물품은 불필요한 알림을 내지 않도록 걸러냅니다.'
    },
    {
      id: 'human_exit',
      wave: 3,
      cat: '동선',
      label: '🚶 사람만 잠깐 하차',
      angle: 90,
      rx: 18,
      ry: 37,
      insight: '사람의 목적지(경유지)와 물건의 목적지(집)가 다름을 구분하는 것이 맥락 지능의 핵심입니다.'
    },
    {
      id: 'five_sec',
      wave: 3,
      cat: '시점',
      label: '⏱️ 하차 직전 5초',
      angle: 156,
      rx: 38,
      ry: 33,
      insight: '기어 P · 목적지 도착 · 적재 기억이 교차하는 정확한 5초에만 개입합니다.'
    },
    {
      id: 'silence',
      wave: 3,
      cat: '판단',
      label: '🔇 주행 중 침묵',
      angle: -128,
      rx: 36,
      ry: 35,
      insight: '언제 말할지뿐 아니라 언제 침묵해야 하는지를 결정하는 지능이 진짜 차량 경험입니다.'
    }
  ];

  // Computed polar positions & starlight-facing reflection vectors per node
  CI_NODES.forEach((n) => {
    const rad = (n.angle * Math.PI) / 180;
    n.x = CI_CENTER.x + Math.cos(rad) * n.rx;
    n.y = CI_CENTER.y + Math.sin(rad) * n.ry;
    n.lightDeg = 90 + n.angle;
    n.lx = -Math.cos(rad);
    n.ly = -Math.sin(rad);
  });

  // Spokes from App Core + Cross-Context Constellation Links
  const CI_EDGES = [
    { from: 'center', to: 'trunk', cross: false },
    { from: 'center', to: 'gear', cross: false },
    { from: 'center', to: 'temp', cross: false },
    { from: 'center', to: 'cabin', cross: false },
    { from: 'trunk', to: 'mart', cross: true },
    { from: 'mart', to: 'fresh', cross: true },
    { from: 'fresh', to: 'temp', cross: true },
    { from: 'temp', to: 'evening', cross: true },
    { from: 'evening', to: 'golf', cross: true },
    { from: 'golf', to: 'silence', cross: true },
    { from: 'gear', to: 'cafe', cross: true },
    { from: 'cafe', to: 'human_exit', cross: true },
    { from: 'human_exit', to: 'silence', cross: true },
    { from: 'cabin', to: 'rain', cross: true },
    { from: 'rain', to: 'home', cross: true },
    { from: 'home', to: 'five_sec', cross: true },
    { from: 'five_sec', to: 'trunk', cross: true },
    { from: 'silence', to: 'trunk', cross: true }
  ];

  let ciStep = 0; // 0: ready, 1..3: bursting waves, 4: connected by app
  let ciFocusedNodeId = null;
  const ciNodeEls = new Map();
  const ciEdgeObjs = [];

  function initContextBurstStage() {
    if (!ciUniverse || !ciNodeLayer || !ciCoreOrb) return;

    CI_NODES.forEach((node) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'ci-context-node';
      btn.dataset.id = node.id;
      btn.dataset.wave = String(node.wave);
      btn.style.setProperty('--light-deg', `${node.lightDeg.toFixed(1)}deg`);
      btn.style.setProperty('--lx', node.lx.toFixed(3));
      btn.style.setProperty('--ly', node.ly.toFixed(3));
      btn.innerHTML = `<span class="ci-node-dot" aria-hidden="true"></span><span class="ci-node-cat">${node.cat}</span><span>${node.label}</span>`;
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        if (ciStep < 4) return;
        ciFocusedNodeId = ciFocusedNodeId === node.id ? null : node.id;
        syncCiFocusState();
      });
      ciNodeLayer.appendChild(btn);
      ciNodeEls.set(node.id, btn);
    });

    const ns = 'http://www.w3.org/2000/svg';
    CI_EDGES.forEach((edge, idx) => {
      const base = document.createElementNS(ns, 'path');
      base.setAttribute('class', `ci-link-path ${edge.cross ? 'is-cross' : ''}`);
      base.setAttribute('pathLength', '1');
      base.style.transitionDelay = `${(idx * 38).toFixed(0)}ms`;

      const pulse = document.createElementNS(ns, 'path');
      pulse.setAttribute('class', 'ci-link-pulse');
      pulse.style.animationDelay = `${(-0.28 * idx).toFixed(2)}s`;

      ciLinkSvg.appendChild(base);
      ciLinkSvg.appendChild(pulse);
      ciEdgeObjs.push({ edge, base, pulse });
    });

    ciUniverse.addEventListener('click', (e) => {
      if (ciStep === 4 && e.target === ciUniverse && ciFocusedNodeId) {
        ciFocusedNodeId = null;
        syncCiFocusState();
      }
    });

    renderCiStage();
  }

  function spawnBurstFx(isConnect) {
    if (!ciFxLayer) return;
    const wave = document.createElement('span');
    wave.className = `ci-shockwave ${isConnect ? 'is-connect' : ''}`;
    ciFxLayer.appendChild(wave);
    setTimeout(() => wave.remove(), 820);

    const sparkCount = isConnect ? 22 : 14;
    for (let i = 0; i < sparkCount; i++) {
      const spark = document.createElement('span');
      spark.className = 'ci-spark';
      const angle = (Math.PI * 2 * i) / sparkCount + (Math.random() - 0.5) * 0.28;
      const dist = (isConnect ? 150 : 100) + Math.random() * 140;
      spark.style.setProperty('--sx', `${(Math.cos(angle) * dist).toFixed(1)}px`);
      spark.style.setProperty('--sy', `${(Math.sin(angle) * dist).toFixed(1)}px`);
      ciFxLayer.appendChild(spark);
      setTimeout(() => spark.remove(), 760);
    }

    ciCoreOrb.classList.add('is-popping');
    setTimeout(() => ciCoreOrb.classList.remove('is-popping'), 170);
  }

  function buildLinkPathD(fromId, toId, w, h) {
    const pA = fromId === 'center' ? CI_CENTER : CI_NODES.find((n) => n.id === fromId);
    const pB = toId === 'center' ? CI_CENTER : CI_NODES.find((n) => n.id === toId);
    if (!pA || !pB) return '';

    const x1 = (pA.x / 100) * w;
    const y1 = (pA.y / 100) * h;
    const x2 = (pB.x / 100) * w;
    const y2 = (pB.y / 100) * h;
    const dx = x2 - x1;
    const dy = y2 - y1;
    const len = Math.max(1, Math.hypot(dx, dy));
    const nx = -dy / len;
    const ny = dx / len;
    const bend = fromId === 'center' ? 14 : 22;
    const cx = (x1 + x2) * 0.5 + nx * bend;
    const cy = (y1 + y2) * 0.5 + ny * bend;
    return `M ${x1.toFixed(1)} ${y1.toFixed(1)} Q ${cx.toFixed(1)} ${cy.toFixed(1)} ${x2.toFixed(1)} ${y2.toFixed(1)}`;
  }

  function syncCiEdgeGeometry() {
    if (!ciLinkSvg || !ciUniverse) return;
    const rect = ciUniverse.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return;

    ciLinkSvg.setAttribute('viewBox', `0 0 ${rect.width.toFixed(0)} ${rect.height.toFixed(0)}`);
    ciEdgeObjs.forEach(({ edge, base, pulse }) => {
      const d = buildLinkPathD(edge.from, edge.to, rect.width, rect.height);
      base.setAttribute('d', d);
      pulse.setAttribute('d', d);
    });
  }

  function syncCiFocusState() {
    if (ciStep < 4) return;
    const connectedIds = new Set();
    if (ciFocusedNodeId) {
      connectedIds.add(ciFocusedNodeId);
      CI_EDGES.forEach((e) => {
        if (e.from === ciFocusedNodeId && e.to !== 'center') connectedIds.add(e.to);
        if (e.to === ciFocusedNodeId && e.from !== 'center') connectedIds.add(e.from);
      });
    }

    ciNodeEls.forEach((el, id) => {
      const isFocus = ciFocusedNodeId === id;
      const isDim = Boolean(ciFocusedNodeId) && !connectedIds.has(id);
      el.classList.toggle('is-focused', isFocus);
      el.classList.toggle('is-dimmed', isDim);
    });

    ciEdgeObjs.forEach(({ edge, base, pulse }) => {
      const touchesFocus = Boolean(ciFocusedNodeId) && (edge.from === ciFocusedNodeId || edge.to === ciFocusedNodeId);
      const isDim = Boolean(ciFocusedNodeId) && !touchesFocus;
      base.classList.toggle('is-lit', touchesFocus);
      base.classList.toggle('is-dimmed', isDim);
      pulse.classList.toggle('is-dimmed', isDim);
    });

    if (ciStatusText) {
      if (ciFocusedNodeId) {
        const node = CI_NODES.find((n) => n.id === ciFocusedNodeId);
        ciStatusText.textContent = node ? `${node.label} — ${node.insight}` : '';
      } else {
        ciStatusText.textContent =
          '✨ 연결 완료! WHAT\'S IN MY TRUNK?가 14개의 흩어진 맥락을 하나로 이었습니다. 아무 단서나 눌러 연결을 확인해 보세요.';
      }
    }
  }

  function renderCiStage() {
    if (!ciUniverse) return;
    ciUniverse.setAttribute('data-step', String(ciStep));
    if (secretPlanEl) secretPlanEl.setAttribute('data-step', String(ciStep));

    ciPips.forEach((pip) => {
      pip.classList.toggle('is-lit', Number(pip.dataset.pip) <= ciStep);
    });

    CI_NODES.forEach((node) => {
      const el = ciNodeEls.get(node.id);
      if (!el) return;
      const isBurst = ciStep >= node.wave;
      const isLinked = ciStep >= 4;
      el.style.left = isBurst ? `${node.x.toFixed(1)}%` : `${CI_CENTER.x}%`;
      el.style.top = isBurst ? `${node.y.toFixed(1)}%` : `${CI_CENTER.y}%`;
      el.classList.toggle('is-burst', isBurst);
      el.classList.toggle('is-linked', isLinked);
      if (!isLinked) {
        el.classList.remove('is-focused', 'is-dimmed');
      }
    });

    syncCiEdgeGeometry();
    ciEdgeObjs.forEach(({ base, pulse }) => {
      const drawn = ciStep >= 4;
      base.classList.toggle('is-drawn', drawn);
      pulse.classList.toggle('is-active', drawn);
      if (!drawn) {
        base.classList.remove('is-lit', 'is-dimmed');
        pulse.classList.remove('is-dimmed');
      }
    });

    if (ciResetBtn) {
      ciResetBtn.hidden = ciStep === 0;
    }

    if (ciStep === 0) {
      if (ciOrbKicker) ciOrbKicker.textContent = 'STEP 0 / 4 · CORE';
      if (ciOrbLabel) ciOrbLabel.textContent = '맥락 터뜨리기';
      if (ciStatusText) ciStatusText.textContent = '아직 연결되지 않은 차량·시간·공간의 단서들이 원 안에 모여 있습니다. 원을 눌러보세요.';
    } else if (ciStep === 1) {
      if (ciOrbKicker) ciOrbKicker.textContent = 'STEP 1 / 4 · SIGNAL';
      if (ciOrbLabel) ciOrbLabel.textContent = '맥락 더 펼치기';
      if (ciStatusText) ciStatusText.textContent = '1차 확산: 트렁크·기어·온도·2열 등 차량의 물리 신호들이 터져 나왔습니다.';
    } else if (ciStep === 2) {
      if (ciOrbKicker) ciOrbKicker.textContent = 'STEP 2 / 4 · CONTEXT';
      if (ciOrbLabel) ciOrbLabel.textContent = '남은 맥락 펼치기';
      if (ciStatusText) ciStatusText.textContent = '2차 확산: 출발지·시간·경유지·기상 등 시공간 맥락이 사방으로 퍼졌습니다.';
    } else if (ciStep === 3) {
      if (ciOrbKicker) ciOrbKicker.textContent = 'STEP 3 / 4 · READY';
      if (ciOrbLabel) ciOrbLabel.textContent = '하나로 연결하기 ✨';
      if (ciStatusText) ciStatusText.textContent = '모든 맥락이 흩어져 떠 있습니다. 원을 한 번 더 눌러 우리 앱으로 하나로 연결해 보세요.';
    } else {
      if (ciOrbKicker) ciOrbKicker.textContent = 'STEP 4 / 4 · LINKED';
      if (ciOrbLabel) ciOrbLabel.textContent = "WHAT'S IN MY TRUNK?";
      syncCiFocusState();
    }
  }

  if (ciCoreOrb) {
    ciCoreOrb.addEventListener('click', (e) => {
      e.stopPropagation();
      if (ciStep < 3) {
        ciStep += 1;
        spawnBurstFx(false);
        renderCiStage();
      } else if (ciStep === 3) {
        ciStep = 4;
        ciFocusedNodeId = null;
        spawnBurstFx(true);
        renderCiStage();
      } else {
        ciFocusedNodeId = null;
        spawnBurstFx(true);
        syncCiFocusState();
      }
    });
  }

  if (ciResetBtn) {
    ciResetBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      ciStep = 0;
      ciFocusedNodeId = null;
      renderCiStage();
    });
  }

  window.addEventListener('resize', syncCiEdgeGeometry);
  initContextBurstStage();

  /* ========================================================================
     8. Pre-Exit Arrival Overlay Popup (Mirrors PreExitOverlayContent.kt)
     ======================================================================== */
  const downloadSectionEl = document.getElementById('download');
  const preExitModal = document.getElementById('preExitModal');
  const preExitBackdrop = document.getElementById('preExitBackdrop');
  const openPreExitModalBtn = document.getElementById('openPreExitModalBtn');
  const preExitCloseBtn = document.getElementById('preExitCloseBtn');
  const preExitAckBtn = document.getElementById('preExitAckBtn');
  const preExitMediaTabs = Array.from(document.querySelectorAll('.pre-exit-tab[data-media-tab]'));
  const preExitVideoStage = document.getElementById('preExitVideoStage');
  const preExitMediaExtLink = document.getElementById('preExitMediaExtLink');
  const preExitTeamName = document.getElementById('preExitTeamName');
  const preExitModalGithubBtn = document.getElementById('preExitModalGithubBtn');
  const preExitModalApkBtn = document.getElementById('preExitModalApkBtn');

  let activePreExitMediaTab = 'demo';
  let hasAutoOpenedPreExit = false;

  function parseMediaUrl(rawUrl) {
    const trimmed = (rawUrl || '').trim();
    if (!trimmed) return { kind: 'empty' };

    if (/\.(mp4|webm|ogg)(\?.*)?$/i.test(trimmed)) {
      return { kind: 'video', embedUrl: trimmed, openUrl: trimmed };
    }

    try {
      const u = new URL(trimmed, window.location.href);
      const host = u.hostname.replace(/^www\./, '');
      if (host === 'youtube.com' || host === 'm.youtube.com') {
        if (u.pathname.startsWith('/shorts/')) {
          const id = u.pathname.split('/shorts/')[1]?.split('/')[0];
          if (id) return { kind: 'iframe', embedUrl: `https://www.youtube.com/embed/${id}?rel=0`, openUrl: trimmed };
        }
        if (u.pathname.startsWith('/embed/')) {
          return { kind: 'iframe', embedUrl: trimmed, openUrl: trimmed };
        }
        const v = u.searchParams.get('v');
        if (v) return { kind: 'iframe', embedUrl: `https://www.youtube.com/embed/${v}?rel=0`, openUrl: trimmed };
      } else if (host === 'youtu.be') {
        const id = u.pathname.replace(/^\//, '').split('/')[0];
        if (id) return { kind: 'iframe', embedUrl: `https://www.youtube.com/embed/${id}?rel=0`, openUrl: trimmed };
      }
    } catch (_) {
      // Fallback to raw URL
    }

    return { kind: 'iframe', embedUrl: trimmed, openUrl: trimmed };
  }

  const PRE_EXIT_MEDIA_TABS = {
    demo: {
      configKey: 'demoVideoUrl',
      title: 'Pleos Connect Demo Video Player',
      emptyLabel: '트렁크 속 Pleos Connect 시연 영상 대기 중'
    },
    card1: {
      configKey: 'cardNews1Url',
      title: 'Card News Video 1 Player',
      emptyLabel: '트렁크 속 카드뉴스 영상 1 대기 중'
    },
    card2: {
      configKey: 'cardNews2Url',
      title: 'Card News Video 2 Player',
      emptyLabel: '트렁크 속 카드뉴스 영상 2 대기 중'
    },
    shorts: {
      configKey: 'youtubeShortsUrl',
      title: 'YouTube Shorts Player',
      emptyLabel: '트렁크 속 YouTube 쇼츠 대기 중'
    }
  };

  function renderPreExitMedia(tabKey) {
    activePreExitMediaTab = Object.prototype.hasOwnProperty.call(PRE_EXIT_MEDIA_TABS, tabKey) ? tabKey : 'demo';
    const meta = PRE_EXIT_MEDIA_TABS[activePreExitMediaTab];
    const isShorts = activePreExitMediaTab === 'shorts';

    preExitMediaTabs.forEach((btn) => {
      const selected = btn.getAttribute('data-media-tab') === activePreExitMediaTab;
      btn.classList.toggle('is-active', selected);
      btn.setAttribute('aria-selected', String(selected));
    });

    if (!preExitVideoStage) return;
    const rawUrl = PRE_EXIT_POPUP_CONFIG[meta.configKey] || '';
    const parsed = parseMediaUrl(rawUrl);

    preExitVideoStage.classList.toggle('is-shorts', isShorts);
    preExitVideoStage.classList.toggle('has-embed', parsed.kind !== 'empty');
    preExitVideoStage.innerHTML = '';

    if (parsed.kind === 'empty') {
      if (preExitMediaExtLink) preExitMediaExtLink.hidden = true;
      const box = document.createElement('div');
      box.className = 'pre-exit-media-placeholder';
      box.innerHTML = `
        <span class="pre-exit-play-orb" aria-hidden="true">▶</span>
        <strong>${meta.emptyLabel}</strong>
        <span><code>PRE_EXIT_POPUP_CONFIG.${meta.configKey}</code>에 링크를 넣으면 바로 재생됩니다</span>
      `;
      preExitVideoStage.appendChild(box);
      return;
    }

    if (preExitMediaExtLink) {
      preExitMediaExtLink.href = parsed.openUrl;
      preExitMediaExtLink.hidden = false;
    }

    if (parsed.kind === 'video') {
      const video = document.createElement('video');
      video.src = parsed.embedUrl;
      video.controls = true;
      video.playsInline = true;
      preExitVideoStage.appendChild(video);
      return;
    }

    const iframe = document.createElement('iframe');
    iframe.src = parsed.embedUrl;
    iframe.title = meta.title;
    iframe.allow = 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share';
    iframe.allowFullscreen = true;
    iframe.loading = 'lazy';
    preExitVideoStage.appendChild(iframe);
  }

  function syncPreExitConfigBindings() {
    if (preExitTeamName && PRE_EXIT_POPUP_CONFIG.teamName) {
      preExitTeamName.textContent = `Team ${PRE_EXIT_POPUP_CONFIG.teamName}`;
    }
    if (preExitModalGithubBtn && PRE_EXIT_POPUP_CONFIG.githubUrl) {
      preExitModalGithubBtn.href = PRE_EXIT_POPUP_CONFIG.githubUrl;
    }
    if (preExitModalApkBtn && PRE_EXIT_POPUP_CONFIG.apkUrl) {
      preExitModalApkBtn.href = PRE_EXIT_POPUP_CONFIG.apkUrl;
    }
  }

  function openPreExitModal(initialTab) {
    if (!preExitModal) return;
    if (initialTab) activePreExitMediaTab = initialTab;
    syncPreExitConfigBindings();
    renderPreExitMedia(activePreExitMediaTab);
    preExitModal.hidden = false;
    document.body.style.overflow = 'hidden';
  }

  function closePreExitModal() {
    if (!preExitModal || preExitModal.hidden) return;
    preExitModal.hidden = true;
    document.body.style.overflow = '';
    if (preExitVideoStage) preExitVideoStage.innerHTML = '';
  }

  if (preExitModal) {
    syncPreExitConfigBindings();

    if (openPreExitModalBtn) {
      openPreExitModalBtn.addEventListener('click', () => openPreExitModal(activePreExitMediaTab));
    }
    if (preExitCloseBtn) {
      preExitCloseBtn.addEventListener('click', closePreExitModal);
    }
    if (preExitAckBtn) {
      preExitAckBtn.addEventListener('click', closePreExitModal);
    }
    if (preExitBackdrop) {
      preExitBackdrop.addEventListener('click', closePreExitModal);
    }
    preExitMediaTabs.forEach((btn) => {
      btn.addEventListener('click', () => renderPreExitMedia(btn.getAttribute('data-media-tab')));
    });

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && !preExitModal.hidden) {
        closePreExitModal();
      }
    });

    // Auto-trigger Pre-Exit popup once upon reaching the destination (#download / page bottom)
    window.addEventListener(
      'scroll',
      () => {
        if (hasAutoOpenedPreExit || !downloadSectionEl) return;
        const rect = downloadSectionEl.getBoundingClientRect();
        const vh = window.innerHeight || 900;
        if (rect.top <= vh * 0.42) {
          hasAutoOpenedPreExit = true;
          openPreExitModal('demo');
        }
      },
      { passive: true }
    );
  }
})();
