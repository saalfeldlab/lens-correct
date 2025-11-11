# LensCorrect

Tools for calibrating and applying lens correction models to multi-channel and split-image microscopy data from light and electron microscopy. This is useful to calculate lens-distortion models for distortion free stitching and/ or chromatic abberation.

There are four CLI sub commands:

```bash
Usage: lens-correct [-hV] [COMMAND]
Calculate and apply lens-distortion corrections from and to multi-channel and
split-image microscopy stacks.
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  help                Display help information about the specified command.
  calibrate-split     Calibrate lens distortion and align split-images or lens
                        arrays
  apply-split         Apply lens-distortion correction and alignment to stacks
                        of split-images or lens arrays
  apply-channels      Apply wavelength-dependent lens-distortion correction and
                        alignment to multi-channel image stacks
  calibrate-channels  Calibrate lens distortion and align multi-channel image
                        stacks
```

## Split calibration

Documented [here](doc/SPLIT.md).

## Multi-channel calibration

Explained [here](https://www.youtube.com/watch?v=lPt-WQuniUs), follow the [split instructions](doc/SPLIT.md).

## Installation

Documented [here](doc/INSTALL.md).

## References

This method is an extension of the method developed for lens-distortion and chromatic aberration correction by [Bogovic et al. (2020). PLOS ONE 15(12): e0236495](https://doi.org/10.1371/journal.pone.0236495) which is an extension of the method developed for lens-distortion corrections and alignment across uncalibrated TEM systems by [Zheng et al. (2018). Cell 174(3):730-743.e22](https://doi.org/10.1016/j.cell.2018.06.019) which is an extension of the method developed for lens-distortion correction of TEM images by [Kaynig et al. (2010). J Struct Biol. 171(2):163-73](https://doi.org/10.1016/j.jsb.2010.04.012) and implemented in [TrakEM2](https://imagej.net/plugins/trakem2/) by [Cardona et al. (2012). PLoS ONE 7(6), e38011](https://doi.org/10.1371/journal.pone.0038011).
