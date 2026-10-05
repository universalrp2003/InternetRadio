package com.universalrp.appforge.model

/** Ready-made starting points so nobody has to stare at a blank screen. */
object Templates {

    fun all(): List<AppProject> = listOf(
        blank(),
        notes(),
        linkHub(),
        tasks(),
    )

    fun blank(): AppProject = AppProject(
        id = newId("prj"),
        name = "My app",
        emoji = "🚀",
        accent = ACCENT_COLORS[0],
        description = "Made with AppForge",
        screens = listOf(
            AppScreen(
                id = newId("scr"),
                title = "Home",
                blocks = listOf(
                    Block(newId("blk"), BlockType.HEADING, "Welcome"),
                    Block(newId("blk"), BlockType.PARAGRAPH, "Tap the pencil to edit this text."),
                    Block(newId("blk"), BlockType.NOTE, "Everything you design here works offline."),
                ),
            )
        ),
    )

    fun notes(): AppProject = AppProject(
        id = newId("prj"),
        name = "My notes",
        emoji = "📝",
        accent = ACCENT_COLORS[2],
        description = "A pocket notebook that saves itself",
        screens = listOf(
            AppScreen(
                id = newId("scr"),
                title = "Note",
                blocks = listOf(
                    Block(newId("blk"), BlockType.HEADING, "Today's note"),
                    Block(newId("blk"), BlockType.FIELD, "Title"),
                    Block(newId("blk"), BlockType.FIELD, "What happened today?"),
                    Block(newId("blk"), BlockType.SAVE, "Save note"),
                    Block(newId("blk"), BlockType.NOTE, "Saved on this phone only — no account, no cloud."),
                ),
            ),
            AppScreen(
                id = newId("scr"),
                title = "About",
                blocks = listOf(
                    Block(newId("blk"), BlockType.HEADING, "How it works"),
                    Block(
                        newId("blk"),
                        BlockType.LIST,
                        "Your text is stored with localStorage\n" +
                            "Closing the app does not erase it\n" +
                            "Nothing is uploaded anywhere",
                    ),
                ),
            ),
        ),
    )

    fun linkHub(): AppProject = AppProject(
        id = newId("prj"),
        name = "Link hub",
        emoji = "🔗",
        accent = ACCENT_COLORS[1],
        description = "All my important links in one place",
        screens = listOf(
            AppScreen(
                id = newId("scr"),
                title = "Links",
                blocks = listOf(
                    Block(newId("blk"), BlockType.HEADING, "My links"),
                    Block(newId("blk"), BlockType.LINK, "GitHub", "https://github.com"),
                    Block(newId("blk"), BlockType.LINK, "YouTube", "https://youtube.com"),
                    Block(newId("blk"), BlockType.BUTTON, "Search the web", "https://duckduckgo.com"),
                    Block(
                        newId("blk"),
                        BlockType.NOTE,
                        "Edit these blocks and paste your own links — a button can also jump to another screen.",
                    ),
                ),
            ),
            AppScreen(
                id = newId("scr"),
                title = "Contact",
                blocks = listOf(
                    Block(newId("blk"), BlockType.HEADING, "Get in touch"),
                    Block(newId("blk"), BlockType.FIELD, "Your email"),
                    Block(newId("blk"), BlockType.SAVE, "Remember email"),
                    Block(newId("blk"), BlockType.PARAGRAPH, "Replace this with your own details."),
                ),
            ),
        ),
    )

    fun tasks(): AppProject = AppProject(
        id = newId("prj"),
        name = "Task list",
        emoji = "✅",
        accent = ACCENT_COLORS[3],
        description = "Tick things off, every day",
        screens = listOf(
            AppScreen(
                id = newId("scr"),
                title = "Today",
                blocks = listOf(
                    Block(newId("blk"), BlockType.HEADING, "Today's tasks"),
                    Block(
                        newId("blk"),
                        BlockType.CHECKLIST,
                        "Drink water\n" +
                            "Walk 30 minutes\n" +
                            "Read 10 pages\n" +
                            "Clean the phone storage 😄",
                    ),
                    Block(newId("blk"), BlockType.NOTE, "Ticks are remembered next time you open the app."),
                ),
            ),
            AppScreen(
                id = newId("scr"),
                title = "Habits",
                blocks = listOf(
                    Block(newId("blk"), BlockType.HEADING, "Weekly habits"),
                    Block(
                        newId("blk"),
                        BlockType.CHECKLIST,
                        "Exercise 3 times\n" +
                            "Call family\n" +
                            "Back up photos",
                    ),
                ),
            ),
        ),
    )
}
