/** All styling for the site. Kept separate from Main.java so the page code stays readable. */
class Css {
    static final String CSS = """
      :root{--leaf:#123b2c;--leaf2:#1d5a43;--leaf-soft:#e3ece6;--turmeric:#f0b323;--turmeric-soft:#fdf3d4;--chili:#c0392b;--chili-soft:#fbe9e6;
        --paper:#f3f6f1;--card:#ffffff;--ink:#17231d;--mute:#5d6d64;--line:#dbe3da;--ok:#1f7a4d;--ok-soft:#e1f3e8;--blue:#2c6a99;--blue-soft:#e5eff7;
        --head:'Bricolage Grotesque','Segoe UI',system-ui,sans-serif;--body:'Figtree','Segoe UI',system-ui,-apple-system,sans-serif}
      *{box-sizing:border-box}[hidden]{display:none!important}
      html{-webkit-text-size-adjust:100%}
      body{margin:0;font-family:var(--body);font-size:16px;line-height:1.5;background:var(--paper);color:var(--ink);font-variant-numeric:tabular-nums}
      h1,h2{font-family:var(--head);margin:0;letter-spacing:-.01em;line-height:1.15}
      h1{font-size:2rem;font-weight:700}h2{font-size:1.25rem;font-weight:600}
      a{color:var(--leaf2)}
      :focus-visible{outline:3px solid var(--turmeric);outline-offset:2px;border-radius:4px}
      header{display:flex;flex-wrap:wrap;gap:8px 28px;align-items:center;justify-content:space-between;background:var(--leaf);padding:12px 28px}
      .brand{display:flex;align-items:center;gap:10px;font-family:var(--head);font-weight:700;font-size:1.25rem;color:#fff;text-decoration:none}
      .plate{width:22px;height:22px;border-radius:50%;background:var(--turmeric);box-shadow:inset 0 0 0 4px var(--leaf),inset 0 0 0 6px var(--turmeric);display:inline-block}
      nav{display:flex;flex-wrap:wrap;gap:4px 22px;align-items:center}
      nav a{color:#c9ddd1;text-decoration:none;padding:6px 0;border-bottom:3px solid transparent}
      nav a:hover{color:#fff}nav a.on{color:#fff;border-bottom-color:var(--turmeric)}
      nav .who{color:#9db8a9;font-size:.92rem}nav a.out{color:#f3c9c3}
      main{max-width:1120px;margin:0 auto;padding:30px 20px 60px}
      .head{display:flex;flex-wrap:wrap;gap:12px;align-items:center;justify-content:space-between;margin:0 0 22px}
      .who2{color:var(--mute)}.who2 b{color:var(--ink)}
      /* buttons & fields */
      .btn{display:inline-flex;align-items:center;justify-content:center;gap:6px;background:var(--leaf);color:#fff;border:2px solid var(--leaf);border-radius:10px;padding:9px 18px;font:600 1rem var(--body);cursor:pointer;text-decoration:none;transition:background .15s,transform .05s}
      .btn:hover{background:var(--leaf2);border-color:var(--leaf2)}.btn:active{transform:translateY(1px)}
      .btn.accent{background:var(--turmeric);border-color:var(--turmeric);color:#2a2000}.btn.accent:hover{background:#ffc83a;border-color:#ffc83a}
      .btn.ghost{background:transparent;color:var(--leaf);border-color:var(--line)}.btn.ghost:hover{background:var(--leaf-soft);border-color:var(--leaf-soft)}
      .btn.danger{background:var(--chili);border-color:var(--chili)}.btn.sm{padding:5px 12px;font-size:.88rem;border-radius:8px}.btn.wide{width:100%;padding:13px 18px;font-size:1.05rem}
      .btn:disabled{background:#d6ddd7;border-color:#d6ddd7;color:#7d8a82;cursor:not-allowed}
      input,select{font:inherit;padding:9px 12px;border:1.5px solid #c5d0c7;border-radius:10px;background:#fff;color:var(--ink);min-height:42px}
      input:focus,select:focus{outline:3px solid var(--turmeric);outline-offset:0;border-color:var(--leaf)}
      label{display:block;font-weight:500;margin:14px 0 5px}
      .filters{display:flex;flex-wrap:wrap;gap:10px;align-items:center;margin:0 0 24px}.filters input[name=kw]{flex:1;min-width:180px}
      .msg{padding:12px 16px;border-radius:10px;margin:0 0 20px;border-left:5px solid}
      .msg.ok{background:var(--ok-soft);color:#14583a;border-color:var(--ok)}.msg.err{background:var(--chili-soft);color:#8a2318;border-color:var(--chili)}
      .muted{color:var(--mute)}.small{font-size:.86rem}
      .empty{background:var(--card);border:1.5px dashed #b9c6bc;border-radius:14px;padding:28px;display:grid;gap:8px;justify-items:start;color:var(--mute)}.empty b{color:var(--ink);font-size:1.1rem}
      .panel{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:20px 22px;margin:0 0 20px}.panel>h2{margin-bottom:12px}
      /* printed-menu rows with dotted leaders */
      .cols{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:12px 56px;align-items:start}
      .cat{margin:0 0 30px}.cat h2{display:flex;align-items:center;gap:10px;padding-bottom:8px;margin-bottom:6px;border-bottom:2px solid var(--leaf)}
      .dot{width:12px;height:12px;border-radius:50%;display:inline-block;background:var(--mute)}
      .c-breakfast{background:var(--turmeric)}.c-meals{background:var(--ok)}.c-snacks{background:var(--chili)}.c-beverages{background:var(--blue)}.c-desserts{background:#94508f}
      .mi{display:flex;align-items:baseline;gap:8px;padding:9px 0}
      .mi .n{font-weight:500}.mi .n small{display:block;font-weight:400;font-size:.8rem;color:var(--mute);line-height:1.3}.mi .n small.low{color:var(--chili)}
      .mi .dots{flex:1;min-width:16px;border-bottom:2px dotted #aebbb1;transform:translateY(-5px)}
      .mi .p{font-weight:600}.mi.off .n,.mi.off .p{color:#8a9890}
      /* order screen */
      .split{display:grid;grid-template-columns:minmax(0,1fr) 340px;gap:40px;align-items:start}
      .listpane .mi{align-items:center;padding:8px 10px;margin:0 -10px;border-radius:10px;border-left:4px solid transparent}
      .listpane .mi .dots{transform:none;margin-top:12px}
      .mi.pick.on{background:var(--turmeric-soft);border-left-color:var(--turmeric)}
      .step{display:inline-flex;align-items:center;margin-left:8px}
      .step input{width:46px;text-align:center;padding:6px 0;min-height:36px;border-radius:0;border-left:0;border-right:0;-moz-appearance:textfield}
      .step input::-webkit-outer-spin-button,.step input::-webkit-inner-spin-button{-webkit-appearance:none;margin:0}
      .sb{width:36px;height:36px;border:1.5px solid #c5d0c7;background:#fff;font:600 1.15rem var(--body);cursor:pointer;color:var(--leaf)}
      .sb:first-child{border-radius:10px 0 0 10px}.sb:last-child{border-radius:0 10px 10px 0}.sb:hover{background:var(--leaf-soft)}
      .slip{position:sticky;top:18px;background:var(--card);border:1px solid var(--line);border-radius:14px;padding:20px;box-shadow:0 10px 30px -18px rgba(18,59,44,.45)}
      .slip h2{margin-bottom:10px}.lines .ln,.tot>div{display:flex;justify-content:space-between;gap:12px;padding:5px 0}
      .lines{min-height:56px;padding-bottom:8px;border-bottom:1px dashed #b9c6bc}
      .tot{margin:8px 0 0}.tot dt{color:var(--mute)}.tot dd{margin:0;font-weight:600;white-space:nowrap}.tot small{color:var(--mute);font-weight:400}
      .tot .disc dt,.tot .disc dd{color:var(--chili)}.tot .grand{border-top:2px solid var(--ink);margin-top:6px;padding-top:10px;align-items:baseline}
      .tot .grand dt{color:var(--ink);font-weight:600}.tot .grand dd{font-family:var(--head);font-size:1.6rem;font-weight:700}
      .pay{border:0;padding:0;margin:14px 0 8px}.pay legend{font-weight:600;padding:0;margin-bottom:4px}
      .pay label{display:flex;align-items:center;gap:8px;margin:6px 0;font-weight:400}.pay input{min-height:0;width:18px;height:18px;accent-color:var(--leaf)}.pay small{color:var(--mute)}
      .warn{background:var(--chili-soft);color:#8a2318;border-radius:10px;padding:10px 12px;margin:8px 0;font-size:.92rem}
      /* receipt */
      .receipt{position:relative;max-width:520px;background:#fff;padding:24px 26px 26px;margin:0 0 34px;box-shadow:0 12px 30px -20px rgba(18,59,44,.55);border-top:6px solid var(--turmeric)}
      .receipt::after{content:'';position:absolute;left:0;right:0;bottom:-10px;height:10px;background:linear-gradient(135deg,#fff 50%,transparent 50%) 0 0/14px 10px repeat-x,linear-gradient(225deg,#fff 50%,transparent 50%) 0 0/14px 10px repeat-x}
      .rh{display:flex;justify-content:space-between;align-items:baseline;padding-bottom:12px;margin-bottom:6px;border-bottom:1px dashed #b9c6bc}.rh b{font-family:var(--head);font-size:1.25rem}.rh span{color:var(--mute)}
      .actions{display:flex;flex-wrap:wrap;gap:10px}
      /* tables */
      table{width:100%;border-collapse:collapse}th,td{padding:11px 8px;border-bottom:1px solid var(--line);text-align:left;vertical-align:middle}
      th{font-size:.84rem;font-weight:600;color:var(--mute)}.r{text-align:right}tr:last-child td{border-bottom:0}tbody tr:hover td{background:#f8faf7}
      .tablewrap{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:6px 16px;margin:0 0 20px;overflow-x:auto}
      td input[type=number]{width:92px;min-height:36px;padding:5px 8px}.act{white-space:nowrap}form.inline{display:inline-flex;gap:6px;margin-left:8px;align-items:center}form.inline select{min-height:34px;padding:4px 8px}
      .pill{display:inline-flex;align-items:center;gap:6px;padding:2px 11px;border-radius:99px;font-size:.82rem;font-weight:600;background:#e9eee9;color:#44544b}
      .pill::before{content:'';width:7px;height:7px;border-radius:50%;background:currentColor}
      .pill.completed,.pill.ready{background:var(--ok-soft);color:#14583a}.pill.placed{background:var(--blue-soft);color:#1d5078}.pill.preparing{background:var(--turmeric-soft);color:#7a5600}.pill.cancelled{background:var(--chili-soft);color:#8a2318}
      details.panel summary{cursor:pointer;font:600 1.1rem var(--head);list-style:none}details.panel summary::-webkit-details-marker{display:none}
      details.panel summary::before{content:'+';display:inline-block;width:1.3em;color:var(--leaf2)}details[open].panel summary::before{content:'\\2212'}
      details.panel[open] summary{margin-bottom:12px}
      /* wallet */
      .balance{background:var(--leaf);color:#fff;border-radius:16px;padding:24px 28px;margin:0 0 20px;display:grid;gap:2px}.balance span{color:#b8d2c3}.balance b{font-family:var(--head);font-size:3rem;line-height:1.1}
      .chips{display:flex;gap:10px;flex-wrap:wrap;margin:0 0 12px}.chip{border:1.5px solid var(--leaf);background:#fff;color:var(--leaf);border-radius:99px;padding:6px 18px;font:600 .95rem var(--body);cursor:pointer}.chip:hover{background:var(--leaf);color:#fff}
      /* dashboard */
      .stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:14px;margin:0 0 22px}
      .stat{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px 18px;display:grid;gap:2px;align-content:start}.stat span{color:var(--mute);font-size:.9rem}.stat b{font-family:var(--head);font-size:1.7rem}
      .stat.lead{background:var(--leaf);border-color:var(--leaf);color:#fff}.stat.lead span{color:#b8d2c3}.stat.lead b{font-size:2.2rem}
      .grid2{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:20px}
      .bar{display:grid;grid-template-columns:140px 1fr auto;gap:12px;align-items:center;padding:6px 0}.bar .bl{font-weight:500}
      .track{height:12px;background:var(--leaf-soft);border-radius:99px;overflow:hidden}.track i{display:block;height:100%;background:var(--leaf2);border-radius:99px}.bar b{min-width:72px;text-align:right}
      /* login / register */
      body.auth{display:grid;grid-template-columns:minmax(0,1.1fr) minmax(0,.9fr);min-height:100vh}
      .hero{background:var(--leaf);color:#fff;padding:40px 56px;display:flex;flex-direction:column;gap:28px;justify-content:center}
      .hero h1{font-size:clamp(2rem,3.6vw,2.9rem);max-width:none}
      .board{background:#0d2c20;border:3px solid #2b6a50;border-radius:12px;padding:20px 26px;max-width:460px}
      .board h2{color:var(--turmeric);font-size:1.1rem;margin-bottom:6px}.board .mi{padding:6px 0;color:#eaf3ed}.board .dots{border-bottom-color:#4d7d68}.board .p{color:var(--turmeric)}
      .formside{display:grid;place-items:center;padding:32px 20px}.authbox{width:100%;max-width:380px}.authbox h2{font-size:1.7rem;margin-bottom:6px}
      .authbox input,.authbox select{width:100%}.authbox .btn{width:100%;margin-top:20px}
      .demo{background:var(--turmeric-soft);border-radius:12px;padding:12px 14px;font-size:.9rem;margin-top:22px;line-height:1.7}code{background:rgba(18,59,44,.09);padding:1px 6px;border-radius:5px}
      @media(max-width:860px){.split{grid-template-columns:1fr}.slip{position:static}body.auth{grid-template-columns:1fr}.hero{padding:28px 22px;order:2}.formside{order:1}header{padding:12px 16px}h1{font-size:1.6rem}.cols{grid-template-columns:1fr}}
      @media(max-width:560px){.bar{grid-template-columns:90px 1fr auto}.listpane .mi .dots{display:none}.listpane .mi .n{flex:1;min-width:0}.step{margin-left:10px}}
      @media(prefers-reduced-motion:reduce){*{transition:none!important}}
    """;
}
