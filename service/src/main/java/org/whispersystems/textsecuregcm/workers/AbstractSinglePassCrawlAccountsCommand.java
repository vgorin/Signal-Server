/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.workers;

import io.dropwizard.Application;
import io.dropwizard.cli.Cli;
import io.dropwizard.cli.EnvironmentCommand;
import io.dropwizard.setup.Environment;
import java.util.Objects;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import org.whispersystems.textsecuregcm.metrics.MetricsUtil;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.util.logging.UncaughtExceptionHandler;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Schedulers;

public abstract class AbstractSinglePassCrawlAccountsCommand extends EnvironmentCommand<WhisperServerConfiguration> {

  private CommandDependencies commandDependencies;
  private Namespace namespace;

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private static final String SEGMENT_COUNT = "segments";

  public AbstractSinglePassCrawlAccountsCommand(final String name, final String description) {
    super(new Application<>() {
      @Override
      public void run(final WhisperServerConfiguration configuration, final Environment environment) {
      }
    }, name, description);
  }

  @Override
  public void configure(final Subparser subparser) {
    super.configure(subparser);

    subparser.addArgument("--segments")
        .type(Integer.class)
        .dest(SEGMENT_COUNT)
        .required(false)
        .setDefault(1)
        .help("The total number of segments for a DynamoDB scan");
  }

  protected CommandDependencies getCommandDependencies() {
    return commandDependencies;
  }

  protected Namespace getNamespace() {
    return namespace;
  }

  @Override
  protected void run(final Environment environment, final Namespace namespace,
      final WhisperServerConfiguration configuration) throws Exception {

    UncaughtExceptionHandler.register();
    MetricsUtil.configureRegistries(configuration, environment);

    this.namespace = namespace;
    this.commandDependencies = CommandDependencies.build(getName(), environment, configuration);

    final int segments = Objects.requireNonNull(namespace.getInt(SEGMENT_COUNT));

    logger.info("Crawling accounts with {} segments and {} processors",
        segments,
        Runtime.getRuntime().availableProcessors());

    final CommandStopListener commandStopListener = new CommandStopListener(configuration.getCommandStopListener());
    try {
      commandStopListener.start();
      crawlAccounts(commandDependencies.accountsManager().streamAllFromDynamo(segments, Schedulers.parallel()));
    } finally {
      commandStopListener.stop();
    }
  }

  @Override
  public void onError(final Cli cli, final Namespace namespace, final Throwable throwable) {
    logger.error("Unhandled error", throwable);
  }

  protected abstract void crawlAccounts(final ParallelFlux<Account> accounts);
}
