<script setup></script>

<template>
  <div class="page">
    <header class="sticky top-0 z-50 flex justify-between items-center px-6 py-4 w-full max-w-7xl mx-auto bg-stone-100/80 backdrop-blur-md">
      <div class="flex items-center gap-3 text-2xl font-bold font-serif text-[#4a7c59]">
        <img src="/app_icon.webp" alt="NanoChat" class="h-12 w-12" />
        NanoChat
      </div>
      <nav class="hidden md:flex items-center gap-8">
        <a class="text-[#4a7c59] font-bold border-b-2 border-[#4a7c59] pb-1 hover:text-[#4a7c59] transition-colors duration-300" href="#features">Features</a>
        <a class="text-stone-600 font-medium hover:text-[#4a7c59] transition-colors duration-300" href="#philosophy">Philosophy</a>
        <a class="text-stone-600 font-medium hover:text-[#4a7c59] transition-colors duration-300" href="#models">Download</a>
      </nav>
      <a class="bg-primary text-on-primary px-6 py-2.5 rounded-lg font-bold hover:opacity-90 transition-opacity" href="#download">Get Started</a>
    </header>

    <main>
      <section class="relative overflow-hidden px-6 pt-20 pb-32 max-w-7xl mx-auto" id="features">
        <div class="grid lg:grid-cols-2 gap-12 items-center">
          <div class="space-y-8">
            <h1 class="text-5xl md:text-7xl font-extrabold text-on-surface tracking-tight leading-[1.1]">
              Privacy First AI, <br /><span class="text-primary">Right on Your Device.</span>
            </h1>
            <p class="text-xl text-on-surface-variant max-w-xl">
              A beautiful Android chat app that runs AI models locally. No internet, no cloud, no big tech. Just you and your data, rooted in total security.
            </p>
            <div class="flex flex-wrap gap-4">
              <a class="bg-primary text-on-primary px-8 py-4 rounded-xl font-bold flex items-center gap-2 hover:shadow-lg transition-all" href="#download">
                <span class="material-symbols-outlined">download</span>
                Download App
              </a>
              <a class="bg-surface-container-high text-primary border border-outline-variant px-8 py-4 rounded-xl font-bold hover:bg-surface-container transition-colors" href="#models">
                View Models
              </a>
            </div>
          </div>
          <div class="relative">
            <div class="bg-primary-container/20 absolute -inset-4 rounded-3xl -rotate-2 blur-2xl"></div>
            <div class="relative bg-black rounded-[3rem] p-3 shadow-2xl border-4 border-stone-800 overflow-hidden max-w-[380px] mx-auto">
              <img alt="NanoChat Android Interface" class="rounded-[2.5rem] w-full object-cover" src="/app_screenshots/model_chat_screen.png" />
            </div>
          </div>
        </div>
      </section>

      <section class="py-24 px-6 bg-surface-container-low" id="features-grid">
        <div class="max-w-7xl mx-auto">
          <div class="mb-16 text-center">
            <h2 class="text-4xl font-bold mb-4">Crafted for Sovereignty</h2>
            <p class="text-on-surface-variant max-w-2xl mx-auto">Experience intelligence without compromising your digital footprint.</p>
          </div>
          <div class="grid grid-cols-1 md:grid-cols-12 gap-6">
            <div class="md:col-span-8 bg-surface p-8 rounded-xl shadow-sm border border-outline-variant/30 flex flex-col justify-between">
              <div>
                <span class="material-symbols-outlined text-primary text-4xl mb-4">signal_wifi_off</span>
                <h3 class="text-2xl font-bold mb-3">No Internet Required</h3>
                <p class="text-on-surface-variant max-w-md">NanoChat functions entirely offline. Whether you're in a remote forest or an airplane, your AI companion is always available without a signal.</p>
              </div>
              <div class="mt-8 overflow-hidden rounded-lg h-48">
                <img alt="Remote mountain landscape" class="w-full h-full object-cover opacity-80" src="https://lh3.googleusercontent.com/aida-public/AB6AXuDCxgM6Pas45eYtrKPEUvoGNrZmr_POR8V30JepnjrXuCWzBtzZHDt-jIjBvRI4pK6pKCe7HdlxRo7X7CgvDkUps1lVbvsgihgE9crVmCyg_fiju5S9nn75PAlPYTGNwpDrOaUiThTLFD8RulxBnmbtRsaoFz0lD_8h5GxWNw2G4cSyZMdPu0kFsZkMTcl52-bscI86FlZaBgoEPE6imQZsCM8ghjAxFCaWBkhkANfK0iLeG0Thyged7tHg57G5gVIkwKr4VWZOu7Nq" />
              </div>
            </div>

            <div class="md:col-span-4 bg-surface p-0 rounded-xl border border-outline-variant/30 overflow-hidden group">
              <div class="p-8">
                <span class="material-symbols-outlined text-tertiary text-4xl mb-4">shield_lock</span>
                <h3 class="text-2xl font-bold mb-3">Total Privacy</h3>
                <p class="text-on-surface-variant text-sm mb-6">Local inference means your prompts never leave your device. Gain full transparency through the AI system overview.</p>
              </div>
              <div class="px-4 pb-0">
                <img alt="Privacy Settings Interface" class="w-full rounded-t-xl shadow-lg translate-y-4 group-hover:translate-y-2 transition-transform duration-500" src="/app_screenshots/app_settings.png" />
              </div>
            </div>

            <div class="md:col-span-4 bg-primary/5 p-8 rounded-xl border border-primary/10">
              <span class="material-symbols-outlined text-primary text-4xl mb-4">volunteer_activism</span>
              <h3 class="text-2xl font-bold mb-3">Ad-Free &amp; Free</h3>
              <p class="text-on-surface-variant">Built as a labor of love. No subscriptions, no hidden fees, and absolutely no data-mining advertisements.</p>
            </div>

            <div class="md:col-span-8 bg-surface p-8 rounded-xl shadow-sm border border-outline-variant/30 flex gap-8 items-center">
              <div class="flex-1">
                <span class="material-symbols-outlined text-primary text-4xl mb-4">format_paint</span>
                <h3 class="text-2xl font-bold mb-3">Jetpack Compose &amp; Material 3</h3>
                <p class="text-on-surface-variant">A native Android experience that feels at home on your device. Fluid animations, dynamic colors, and an intuitive organic flow.</p>
              </div>
              <div class="hidden sm:flex w-48 h-32 bg-primary/10 rounded-full items-center justify-center">
                <span class="material-symbols-outlined text-primary text-6xl leading-none">android</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="py-32 px-6" id="philosophy">
        <div class="max-w-5xl mx-auto flex flex-col items-center text-center">
          <div class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-tertiary-container/20 text-tertiary font-bold text-sm mb-8">
            <span class="material-symbols-outlined text-sm">psychology</span>
            OUR PHILOSOPHY
          </div>
          <h2 class="text-4xl md:text-6xl font-bold mb-8 leading-tight">
            No infrastructure, <br /><span class="italic text-primary">no mercy of corporations.</span>
          </h2>
          <p class="text-xl text-on-surface-variant leading-relaxed mb-12 max-w-3xl">
            We believe that artificial intelligence should be a utility, not a leash. In an era of centralized control, NanoChat is built to be a reliable companion that stays with you even when global systems falter. It is an independent tool for independent minds.
          </p>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-12 w-full mt-8">
            <div class="space-y-4">
              <div class="text-primary font-bold text-lg">Resilience</div>
              <p class="text-on-surface-variant text-sm">Engineered to work on mid-range hardware without cloud dependency.</p>
            </div>
            <div class="space-y-4">
              <div class="text-primary font-bold text-lg">Sovereignty</div>
              <p class="text-on-surface-variant text-sm">Your data remains your own stored locally.</p>
            </div>
            <div class="space-y-4">
              <div class="text-primary font-bold text-lg">Transparency</div>
              <p class="text-on-surface-variant text-sm">Open source foundations that allow you to verify every byte of processing.</p>
            </div>
          </div>
        </div>
      </section>

      <section class="py-24 px-6 bg-stone-100" id="models">
        <div class="max-w-7xl mx-auto">
          <div class="flex flex-col md:flex-row justify-between items-end mb-16 gap-6">
            <div>
              <h2 class="text-4xl font-bold mb-4">Choose Your Mind</h2>
              <p class="text-on-surface-variant max-w-xl">Support for a wide variety of quantized models optimized for mobile NPU and GPU inference. Powered by the global community.</p>
            </div>
            <a class="text-primary font-bold flex items-center gap-2 hover:underline" href="https://huggingface.co/adiagarwal/nanochat-models" target="_blank" rel="noreferrer">
              Explore on Hugging Face
              <span class="material-symbols-outlined">open_in_new</span>
            </a>
          </div>

          <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
            <div class="lg:col-span-2 bg-surface p-8 rounded-2xl shadow-sm border border-outline-variant/50 flex flex-col md:flex-row gap-8 items-center overflow-hidden">
              <div class="flex-1 space-y-4">
                <div class="w-12 h-12 rounded-lg bg-primary/10 flex items-center justify-center text-primary">
                  <span class="material-symbols-outlined">model_training</span>
                </div>
                <h3 class="text-2xl font-bold">Local Model Library</h3>
                <p class="text-on-surface-variant">Download and manage your on-device intelligence. Choose between lightweight models for speed or larger ones for depth.</p>
                <div class="flex flex-wrap gap-2">
                  <span class="px-3 py-1 bg-surface-container text-xs font-bold rounded-full">Gemma-3</span>
                  <span class="px-3 py-1 bg-surface-container text-xs font-bold rounded-full">Llama-3</span>
                  <span class="px-3 py-1 bg-surface-container text-xs font-bold rounded-full">Phi-3</span>
                  <span class="px-3 py-1 bg-surface-container text-xs font-bold rounded-full">DeepSeek</span>
                </div>
              </div>
              <div class="w-full md:w-64 flex-shrink-0">
                <img alt="Local Models Screen" class="rounded-xl shadow-xl border border-outline-variant/30 w-full" src="/app_screenshots/model_manager_list.png" />
              </div>
            </div>

            <div class="space-y-8">
              <div class="bg-surface p-8 rounded-2xl shadow-sm border border-outline-variant/50">
                <div class="w-12 h-12 rounded-lg bg-tertiary/10 flex items-center justify-center text-tertiary mb-6">
                  <span class="material-symbols-outlined">code</span>
                </div>
                <h3 class="text-xl font-bold mb-2">Logic &amp; Coding</h3>
                <p class="text-sm text-on-surface-variant">Specialized models for programming assistance and complex mathematical reasoning.</p>
              </div>
              <div class="bg-surface p-8 rounded-2xl shadow-sm border border-outline-variant/50 relative overflow-hidden">
                <div class="absolute top-4 right-4 bg-primary text-on-primary text-[10px] font-bold px-2 py-0.5 rounded uppercase">Coming soon</div>
                <div class="w-12 h-12 rounded-lg bg-surface-variant flex items-center justify-center text-on-surface-variant mb-6">
                  <span class="material-symbols-outlined">visibility</span>
                </div>
                <h3 class="text-xl font-bold mb-2">Multi-Modality</h3>
                <p class="text-sm text-on-surface-variant">Upcoming support for image understanding and audio processing directly on device.</p>
              </div>
            </div>
          </div>

          <div class="mt-16 p-8 bg-[#4a7c59] rounded-2xl text-on-primary flex flex-col md:flex-row items-center gap-8 justify-between" id="download">
            <div class="max-w-lg text-center md:text-left">
              <h3 class="text-2xl font-bold mb-2">Ready to take control?</h3>
              <p class="opacity-90">Download the latest APK and start chatting with local intelligence today. No account needed.</p>
            </div>
            <a class="bg-white text-primary px-10 py-4 rounded-xl font-bold whitespace-nowrap hover:bg-stone-100 transition-colors" href="https://github.com/adiagarwalrock/NanoChat/releases" target="_blank" rel="noreferrer">
              Download for Android
            </a>
          </div>
        </div>
      </section>
    </main>

    <footer class="w-full py-12 px-6 flex flex-col md:flex-row justify-between items-center gap-4 bg-stone-100 border-t border-stone-200">
      <div class="flex flex-col gap-2 items-center md:items-start">
        <div class="text-lg font-serif text-[#4a7c59]">NanoChat</div>
        <p class="text-stone-500 text-sm">© 2024 NanoChat. Rooted in Privacy.</p>
      </div>
      <div class="flex gap-8">
        <a class="text-stone-500 text-sm hover:text-[#4a7c59] transition-colors" href="https://docs.google.com/document/d/1RsLlKxclAe3LfuaDMTz6CjWW3-4P-i-P_X6IJHjvtb0" target="_blank" rel="noreferrer">Privacy Policy</a>
        <a class="text-stone-500 text-sm hover:text-[#4a7c59] transition-colors" href="https://huggingface.co/adiagarwal/nanochat-models" target="_blank" rel="noreferrer">Hugging Face</a>
        <a class="text-stone-500 text-sm hover:text-[#4a7c59] transition-colors" href="https://github.com/adiagarwalrock/NanoChat" target="_blank" rel="noreferrer">GitHub</a>
      </div>
      <div class="flex gap-4 text-stone-400">
        <span class="material-symbols-outlined">eco</span>
        <span class="material-symbols-outlined">lock</span>
      </div>
    </footer>
  </div>
</template>
