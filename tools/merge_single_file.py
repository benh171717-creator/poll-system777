#!/usr/bin/env python3
"""
Merges the whole Maven project into a single Main.java file.

The submission box of the course site accepts one JAVA block, so this produces a
single compilable file: all classes move to the default package, every top-level
`public` modifier is dropped (Java allows only one public top-level type per file)
and the imports are hoisted and de-duplicated.
"""
import os
import re
import sys

SRC = "src/main/java/com/pollsystem"
OUT = "submission/Main.java"

HEADER = """/* =====================================================================================
 *  מערכת ניהול סקרים - בוט Telegram + ממשק ניהול ב-Java Swing
 *  -----------------------------------------------------------------------------------
 *  קובץ זה הוא איחוד אוטומטי של כל מחלקות הפרויקט לקובץ יחיד, לצורך הגשה.
 *  גרסת הפרויקט המלאה (Maven, מחלקה לכל קובץ) מסודרת בחבילות:
 *      com.pollsystem.model | .service | .bot | .ui | .config
 *
 *  הרצה:
 *      javac -encoding UTF-8 -cp "telegrambots-6.9.7.1.jar:jackson-databind-2.17.2.jar:..." Main.java
 *      java  -cp ".:telegrambots-6.9.7.1.jar:..." Main
 *  (או פשוט: mvn exec:java  בפרויקט המלא)
 *
 *  תלויות:
 *      org.telegram : telegrambots            : 6.9.7.1
 *      com.fasterxml.jackson.core : jackson-databind : 2.17.2
 *
 *  בהפעלה ראשונה נפתח חלון הגדרות להזנת טוקן הבוט ומפתח OpenAI (נשמר ב-config.properties).
 * ===================================================================================== */

"""

# The order only affects readability - Java does not care.
ORDER = [
    "config/AppConfig.java",
    "model/Member.java",
    "model/Question.java",
    "model/PollStatus.java",
    "model/ParticipantProgress.java",
    "model/Poll.java",
    "service/CommunityListener.java",
    "service/CommunityService.java",
    "service/BotGateway.java",
    "service/PollListener.java",
    "service/PollService.java",
    "service/ChatGptService.java",
    "bot/SurveyBot.java",
    "ui/Theme.java",
    "ui/HintTextField.java",
    "ui/SetupDialog.java",
    "ui/CommunityPanel.java",
    "ui/CreatePollPanel.java",
    "ui/LivePollPanel.java",
    "ui/ResultsPanel.java",
    "ui/MainWindow.java",
    "Main.java",
]

TOP_LEVEL_PUBLIC = re.compile(r"^public\s+(final\s+|abstract\s+)?(class|interface|enum|record)\s", re.M)
PACKAGE_PREFIX = re.compile(r"\bcom\.pollsystem\.(model|service|bot|ui|config)\.")


def main():
    imports = set()
    bodies = []

    for relative in ORDER:
        path = os.path.join(SRC, relative)
        if not os.path.exists(path):
            sys.exit("missing source file: " + path)
        with open(path, encoding="utf-8") as handle:
            text = handle.read()

        lines = []
        for line in text.split("\n"):
            stripped = line.strip()
            if stripped.startswith("package "):
                continue
            if stripped.startswith("import "):
                imports.add(stripped)
                continue
            lines.append(line)

        body = "\n".join(lines).strip("\n")
        # only one public top-level type is allowed per file
        body = TOP_LEVEL_PUBLIC.sub(lambda m: m.group(0)[len("public "):], body)
        # fully-qualified references no longer make sense in the default package
        body = PACKAGE_PREFIX.sub("", body)

        bodies.append("// " + "=" * 84 + "\n// " + relative + "\n// " + "=" * 84 + "\n\n" + body)

    # Main must stay public so the file can be called Main.java
    bodies[-1] = bodies[-1].replace("class Main {", "public class Main {", 1)

    ordered_imports = sorted(i for i in imports if not i.startswith("import com.pollsystem"))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as handle:
        handle.write(HEADER)
        handle.write("\n".join(ordered_imports))
        handle.write("\n\n")
        handle.write("\n\n".join(bodies))
        handle.write("\n")

    with open(OUT, encoding="utf-8") as handle:
        print("wrote %s (%d lines)" % (OUT, len(handle.readlines())))


if __name__ == "__main__":
    main()
