package me.bechberger.jfrredact.commands;

import picocli.CommandLine.*;

/**
 * Words command group for discovering and redacting words/strings
 */
@Command(
    name = "words",
    description = "Discover and redact words/strings in JFR events or text files",
    mixinStandardHelpOptions = true,
    subcommands = {
        WordsDiscoverCommand.class,
        WordsRedactCommand.class
    }
)
public class WordsCommand {
    // This is just a command group, no execution
}