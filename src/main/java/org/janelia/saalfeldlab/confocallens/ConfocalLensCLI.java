/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.janelia.saalfeldlab.confocallens;

import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * CLI tool for confocal lens correction
 *
 * Subcommands:
 * - calibrate-split: Calibrate lens distortion and align split-images or lens arrays
 * - calibrate-channels: Calibrate lens distortion and align multi-channel image stacks
 * - apply-split: Apply lens calibration and alignment to stacks of split-images or lens arrays
 * - apply-channels: Apply lens calibration and alignment to multi-channel image stacks
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
@Command(
    name = "confocal-lens",
    mixinStandardHelpOptions = true,
    version = "confocal-lens 0.0.1-SNAPSHOT",
    description = "Calculate and apply lens-distortion corrections from confocal stacks",
    subcommands = {
        HelpCommand.class,
        CalibrateSplit.class,
        ApplySplit.class,
        ApplyChannels.class,
        CalibrateChannels.class
    }
)
public class ConfocalLensCLI implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        // If no subcommand is specified, show usage
        spec.commandLine().usage(System.err);
        return 1;
    }

    public static void main(String[] args) {
        final CommandLine cmd = new CommandLine(new ConfocalLensCLI());
        cmd.setExecutionStrategy(new CommandLine.RunAll());
        System.exit(cmd.execute(args));
    }
}