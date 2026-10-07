package com.universalrp.appforge.model

/**
 * Turns an [AppProject] into one self-contained HTML file.
 *
 * The file is a real, working mini app: screens switch with a nav bar, text
 * fields and checklists save themselves into localStorage, and it opens in any
 * browser (or inside AppForge's own preview WebView) with no server at all.
 */
object HtmlRenderer {

    fun render(project: AppProject): String {
        val sb = StringBuilder()
        val accent = project.accent.ifBlank { "#22D3EE" }
        val multi = project.screens.size > 1

        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
        sb.append("<meta charset=\"utf-8\">\n")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n")
        sb.append("<meta name=\"theme-color\" content=\"").append(escapeAttr(accent)).append("\">\n")
        sb.append("<meta name=\"apple-mobile-web-app-capable\" content=\"yes\">\n")
        sb.append("<title>").append(escapeHtml(project.name)).append("</title>\n")
        sb.append("<style>\n").append(css(accent)).append("\n</style>\n")
        sb.append("</head>\n<body>\n")

        // ------------------------------------------------------------- header
        sb.append("<header class=\"top\">\n")
        sb.append("<div class=\"brand\"><span class=\"emoji\">").append(escapeHtml(project.emoji)).append("</span>")
        sb.append("<span class=\"name\">").append(escapeHtml(project.name)).append("</span></div>\n")
        if (project.description.isNotBlank()) {
            sb.append("<p class=\"desc\">").append(escapeHtml(project.description)).append("</p>\n")
        }
        if (multi) {
            sb.append("<nav class=\"nav\">\n")
            project.screens.forEachIndexed { index, screen ->
                sb.append("<button class=\"navbtn")
                if (index == 0) sb.append(" on")
                sb.append("\" data-target=\"").append(escapeAttr(screen.id)).append("\">")
                sb.append(escapeHtml(screen.title)).append("</button>\n")
            }
            sb.append("</nav>\n")
        }
        sb.append("</header>\n")

        // ------------------------------------------------------------ screens
        sb.append("<main>\n")
        project.screens.forEachIndexed { index, screen ->
            sb.append("<section class=\"screen\" data-screen=\"").append(escapeAttr(screen.id)).append("\"")
            if (index != 0) sb.append(" style=\"display:none\"")
            sb.append(">\n")
            if (!multi && screen.title.isNotBlank()) {
                sb.append("<h1 class=\"sr-only\">").append(escapeHtml(screen.title)).append("</h1>\n")
            }
            var fieldIndex = 0
            var checkIndex = 0
            screen.blocks.forEach { block ->
                when (block.type) {
                    BlockType.HEADING ->
                        sb.append("<h2>").append(escapeHtml(block.text)).append("</h2>\n")

                    BlockType.PARAGRAPH ->
                        sb.append("<p>").append(escapeHtml(block.text)).append("</p>\n")

                    BlockType.NOTE ->
                        sb.append("<div class=\"note\">").append(escapeHtml(block.text)).append("</div>\n")

                    BlockType.LIST -> {
                        sb.append("<ul>\n")
                        lines(block.text).forEach { item ->
                            sb.append("<li>").append(escapeHtml(item)).append("</li>\n")
                        }
                        sb.append("</ul>\n")
                    }

                    BlockType.LINK -> {
                        val href = block.href.ifBlank { "#" }
                        sb.append("<a class=\"link\" href=\"").append(escapeAttr(href))
                            .append("\" target=\"_blank\" rel=\"noopener\">")
                            .append(escapeHtml(block.text.ifBlank { href })).append("</a>\n")
                    }

                    BlockType.BUTTON -> {
                        val label = escapeHtml(block.text.ifBlank { "Open" })
                        if (block.href.startsWith("#screen:")) {
                            val target = block.href.removePrefix("#screen:")
                            sb.append("<button class=\"btn\" data-go=\"").append(escapeAttr(target)).append("\">")
                                .append(label).append("</button>\n")
                        } else if (block.href.isBlank()) {
                            sb.append("<button class=\"btn\">").append(label).append("</button>\n")
                        } else {
                            sb.append("<a class=\"btn\" href=\"").append(escapeAttr(block.href))
                                .append("\" target=\"_blank\" rel=\"noopener\">").append(label).append("</a>\n")
                        }
                    }

                    BlockType.IMAGE -> {
                        sb.append("<figure>")
                        sb.append("<img src=\"").append(escapeAttr(block.href)).append("\" alt=\"")
                            .append(escapeAttr(block.text)).append("\" loading=\"lazy\">")
                        if (block.text.isNotBlank()) {
                            sb.append("<figcaption>").append(escapeHtml(block.text)).append("</figcaption>")
                        }
                        sb.append("</figure>\n")
                    }

                    BlockType.FIELD -> {
                        val slot = "f" + fieldIndex
                        sb.append("<label class=\"field\">")
                        sb.append("<span>").append(escapeHtml(block.text.ifBlank { "Text field" })).append("</span>")
                        sb.append("<input type=\"text\" data-save=\"").append(escapeAttr(slot))
                            .append("\" placeholder=\"").append(escapeAttr(block.text)).append("\">")
                        sb.append("</label>\n")
                        fieldIndex++
                    }

                    BlockType.SAVE ->
                        sb.append("<button class=\"btn save\" onclick=\"AF.save(this)\">")
                            .append(escapeHtml(block.text.ifBlank { "Save" })).append("</button>\n")

                    BlockType.CHECKLIST -> {
                        sb.append("<div class=\"checklist\">\n")
                        lines(block.text).forEach { item ->
                            val slot = "c" + checkIndex
                            sb.append("<label class=\"chk\"><input type=\"checkbox\" data-chk=\"")
                                .append(escapeAttr(slot)).append("\"><span>")
                                .append(escapeHtml(item)).append("</span></label>\n")
                            checkIndex++
                        }
                        sb.append("</div>\n")
                    }

                    BlockType.DIVIDER ->
                        sb.append("<hr>\n")
                }
            }
            sb.append("</section>\n")
        }
        sb.append("</main>\n")
        sb.append("<footer>Built with AppForge — works fully offline.</footer>\n")
        sb.append("<script>\n").append(script(project.id)).append("\n</script>\n")
        sb.append("</body>\n</html>\n")
        return sb.toString()
    }

    // ------------------------------------------------------------------ parts

    private fun css(accent: String): String = """
        :root { --accent: $accent; --bg:#0B0F1E; --card:#131A2E; --line:#232C46; --text:#E7ECF7; --dim:#96A2BE; }
        * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
        body { margin:0; padding:0; background:var(--bg); color:var(--text);
               font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
               line-height:1.55; padding-bottom:64px; }
        header.top { padding:22px 18px 12px; background:linear-gradient(160deg, var(--accent) 0%, transparent 70%);
                     border-bottom:1px solid var(--line); }
        .brand { display:flex; align-items:center; gap:10px; }
        .emoji { font-size:30px; }
        .name { font-size:24px; font-weight:800; letter-spacing:-0.3px; }
        .desc { color:var(--dim); margin:8px 0 0; font-size:14px; }
        .nav { display:flex; gap:8px; overflow-x:auto; margin-top:14px; padding-bottom:2px; }
        .navbtn { flex:0 0 auto; background:rgba(255,255,255,0.08); color:var(--text); border:1px solid var(--line);
                  border-radius:999px; padding:8px 14px; font-size:14px; font-weight:600; }
        .navbtn.on { background:var(--accent); color:#04121A; border-color:var(--accent); }
        main { padding:18px; max-width:720px; margin:0 auto; }
        h1,h2 { line-height:1.25; }
        h2 { font-size:21px; margin:22px 0 8px; }
        p { margin:10px 0; color:#D6DDF0; }
        ul { margin:10px 0; padding-left:22px; }
        li { margin:5px 0; color:#D6DDF0; }
        .note { background:var(--card); border-left:4px solid var(--accent); border-radius:12px;
                padding:12px 14px; margin:14px 0; color:#D6DDF0; }
        .btn { display:inline-block; margin:10px 0; background:var(--accent); color:#04121A; border:none;
               border-radius:14px; padding:13px 18px; font-size:15px; font-weight:700; text-decoration:none; }
        .link { color:var(--accent); font-weight:600; text-decoration:none; display:inline-block; margin:8px 0; }
        figure { margin:16px 0; }
        img { width:100%; border-radius:14px; border:1px solid var(--line); display:block; }
        figcaption { color:var(--dim); font-size:13px; margin-top:6px; }
        .field { display:block; margin:14px 0; }
        .field span { display:block; font-size:13px; color:var(--dim); margin-bottom:6px; font-weight:600; }
        .field input { width:100%; background:var(--card); border:1px solid var(--line); border-radius:12px;
                       padding:12px; color:var(--text); font-size:15px; }
        .field input:focus { outline:none; border-color:var(--accent); }
        .chk { display:flex; align-items:center; gap:10px; padding:11px 12px; background:var(--card);
               border:1px solid var(--line); border-radius:12px; margin:8px 0; }
        .chk input { width:20px; height:20px; accent-color:var(--accent); }
        .chk input:checked + span { color:var(--dim); text-decoration:line-through; }
        hr { border:none; border-top:1px solid var(--line); margin:18px 0; }
        footer { text-align:center; color:var(--dim); font-size:12px; padding:26px 18px; }
        .toast { position:fixed; left:50%; bottom:26px; transform:translateX(-50%); background:var(--accent);
                 color:#04121A; font-weight:700; padding:10px 18px; border-radius:999px; opacity:0;
                 transition:opacity .25s; pointer-events:none; }
        .toast.show { opacity:1; }
        .sr-only { position:absolute; width:1px; height:1px; overflow:hidden; clip:rect(0 0 0 0); }
    """.trimIndent()

    private fun script(projectId: String): String = """
        var AF = (function () {
          var NS = "af." + "$projectId" + ".";
          function key(slot) { return NS + slot; }
          function show(id) {
            var screens = document.querySelectorAll(".screen");
            for (var i = 0; i < screens.length; i++) {
              screens[i].style.display = screens[i].getAttribute("data-screen") === id ? "block" : "none";
            }
            var btns = document.querySelectorAll(".navbtn");
            for (var j = 0; j < btns.length; j++) {
              btns[j].className = btns[j].getAttribute("data-target") === id ? "navbtn on" : "navbtn";
            }
            try { localStorage.setItem(NS + "open", id); } catch (e) {}
          }
          function toast(msg) {
            var t = document.getElementById("af-toast");
            if (!t) {
              t = document.createElement("div");
              t.id = "af-toast";
              t.className = "toast";
              document.body.appendChild(t);
            }
            t.textContent = msg;
            t.className = "toast show";
            setTimeout(function () { t.className = "toast"; }, 1400);
          }
          function save(btn) {
            var fields = document.querySelectorAll("[data-save]");
            for (var i = 0; i < fields.length; i++) {
              try { localStorage.setItem(key(fields[i].getAttribute("data-save")), fields[i].value); } catch (e) {}
            }
            if (btn) { var old = btn.textContent; btn.textContent = "Saved"; setTimeout(function () { btn.textContent = old; }, 1200); }
            toast("Saved on this device");
          }
          function restore() {
            var fields = document.querySelectorAll("[data-save]");
            for (var i = 0; i < fields.length; i++) {
              try {
                var v = localStorage.getItem(key(fields[i].getAttribute("data-save")));
                if (v !== null) { fields[i].value = v; }
              } catch (e) {}
            }
            var boxes = document.querySelectorAll("[data-chk]");
            for (var j = 0; j < boxes.length; j++) {
              try { boxes[j].checked = localStorage.getItem(key(boxes[j].getAttribute("data-chk"))) === "1"; } catch (e) {}
            }
          }
          function init() {
            restore();
            var boxes = document.querySelectorAll("[data-chk]");
            for (var j = 0; j < boxes.length; j++) {
              boxes[j].addEventListener("change", function () {
                try { localStorage.setItem(key(this.getAttribute("data-chk")), this.checked ? "1" : "0"); } catch (e) {}
              });
            }
            var goers = document.querySelectorAll("[data-go]");
            for (var k = 0; k < goers.length; k++) {
              goers[k].addEventListener("click", function () { show(this.getAttribute("data-go")); });
            }
            var btns = document.querySelectorAll(".navbtn");
            for (var m = 0; m < btns.length; m++) {
              btns[m].addEventListener("click", function () { show(this.getAttribute("data-target")); });
            }
            var first = document.querySelector(".screen");
            var open = null;
            try { open = localStorage.getItem(NS + "open"); } catch (e) {}
            if (open && document.querySelector('[data-screen="' + open + '"]')) { show(open); }
            else if (first) { show(first.getAttribute("data-screen")); }
          }
          if (document.readyState === "loading") { document.addEventListener("DOMContentLoaded", init); }
          else { init(); }
          return { show: show, save: save, toast: toast };
        })();
    """.trimIndent()

    // ---------------------------------------------------------------- helpers

    private fun lines(text: String): List<String> =
        text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    fun escapeAttr(s: String): String = escapeHtml(s)
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
