### Other systems prerequisites:

Initialize OpenCL C/C++ device parameters and check requirements:

        $ mx mg --detect-ocl-platform
        $ mx mg --check-requirements


Install Anaconda2, Anaconda3 and PyPy3.

        $ wget https://repo.continuum.io/archive/Anaconda3-4.4.0-Linux-x86_64.sh
        $ chmod +x Anaconda3-4.4.0-Linux-x86_64.sh
        $ ./Anaconda3-4.4.0-Linux-x86_64.sh -b -p $MEGAGUARDS_ROOT/anaconda3
        $ wget https://repo.continuum.io/archive/Anaconda2-5.1.0-Linux-x86_64.sh
        $ chmod +x Anaconda2-5.1.0-Linux-x86_64.sh
        $ ./Anaconda2-5.1.0-Linux-x86_64.sh -b -p $MEGAGUARDS_ROOT/anaconda2
        $ wget https://bitbucket.org/pypy/pypy/downloads/pypy3-v5.10.0-linux64.tar.bz2
        $ tar -xjf pypy3-v5.10.0-linux64.tar.bz2
        $ chmod +x Anaconda2-5.1.0-Linux-x86_64.sh
        $ ./Anaconda2-5.1.0-Linux-x86_64.sh -b -p $MEGAGUARDS_ROOT/anaconda2


Add the following environment variables to you favorite shell. e.g. `~/.bashrc` for `bash`:

        MEGAGUARDS_ROOT=/path/to/megaguards ## replace path with correct one. ##
        PATH=$MEGAGUARDS_ROOT/pypy3-v5.10.0-linux64:$PATH
        PATH=$MEGAGUARDS_ROOT/anaconda3:$PATH
        PATH=$MEGAGUARDS_ROOT/anaconda2:$PATH
        PATH=$MEGAGUARDS_ROOT/mx:$PATH
        JAVA_HOME=$MEGAGUARDS_ROOT/labsjdk1.8.0_151-jvmci-0.39



### Test all the installed tools (pre-benchmarking):

    $ mx mg --simple-example


### Benchmarking

MegaGuards benchmark suite is completely automated. The benchmark suite will collect and process all the benchmarks output information into `JSON` files at `zippy-megaguards/asv/results/<YOUR_MACHINE_NAME>`. Each system will have it own
`JSON` file with all the reported numbers.

When exeuting the benchmark suite for the first time, you will be asked to answer some questions about your system
which will be added to the metadata of each `JSON file`.

To accelerate benchmarking time we suggest running the default options.

Time estimation (based on our machine) for a single run of:

- CPython3 (24 Hours) (Using 1 step warm up)
- PyPy3 (10 Hours) (Using 3 steps warm up)
- ZipPy (18 Hours) (Using 8 steps warm up)
- MG-Truffle (10 Hours) (Using 8 steps warm up)
- MG-GPU (35 Minutes) (Using 8 steps warm up)
- MG-CPU (42 Minutes) (Using 8 steps warm up)
- MG-Adaptive (40 Minutes) (Using 8 steps warm up)
- OpenCL-GPU C/C++ (12 Minutes) (Using 8 steps warm up)
- OpenCL-CPU C/C++ (15 Minutes) (Using 8 steps warm up)



You can change our default warm up count step in `zippy-megaguards/mx.zippy/mx_zippy_bench_param.py` and change the count in `mg_warmup_count` dictionary.

##### Sample benchmark:

- Option 1:


    $ cd $MEGAGUARDS_ROOT/zippy-megaguards
    $ ./run-sample-bench.sh

- Option 2:


    $ cd $MEGAGUARDS_ROOT/zippy-megaguards
    $ mx asv-benchmark "mgbench-cpython3.5-hpc-rodinia:*" --smallest
    $ mx asv-benchmark "mgbench-zippy-hpc-rodinia:*" --smallest
    $ mx asv-benchmark "mgbench-pypy3-hpc-rodinia:*" --smallest
    $ mx asv-benchmark "mgbench-opencl-gpu-hpc-rodinia:*" --smallest
    $ mx asv-benchmark "mgbench-opencl-cpu-hpc-rodinia:*" --smallest
    $ mx asv-benchmark "mgbench-zippy-mg-cpu-hpc-rodinia:*" --smallest
    $ mx asv-benchmark "mgbench-zippy-mg-gpu-hpc-rodinia:*" --smallest
    $ mx asv-benchmark "mgbench-zippy-mg-hpc-rodinia:*" --smallest


Please delete all the generated files in `$MEGAGUARDS_ROOT/zippy-megaguards/asv/results/<YOUR_MACHINE_NAME>` before running the complete benchmark.

##### Full benchmark:


    $ cd $MEGAGUARDS_ROOT/zippy-megaguards
    $ ./run.sh


If your CPU has less than 8 Compute Units, i.e less than 4 Core with 2 threads each, *please disable the lines `$MEGAGUARDS_ROOT/zippy-megaguards/run.sh` that corresponds to this*.

For example, if you CPU has only 2 Core and 2 threads each edit `run.sh`


    ...
    # CPU Scaling
    export NUM_CPU_CORES=1
    mx asv-benchmark "mgbench-zippy-mg-cpu1-hpc-rodinia:*"
    export NUM_CPU_CORES=2
    mx asv-benchmark "mgbench-zippy-mg-cpu2-hpc-rodinia:*"
    export NUM_CPU_CORES=4
    mx asv-benchmark "mgbench-zippy-mg-cpu4-hpc-rodinia:*"
    export NUM_CPU_CORES=6
    mx asv-benchmark "mgbench-zippy-mg-cpu6-hpc-rodinia:*"


To


    ...
    # CPU Scaling
    export NUM_CPU_CORES=1
    mx asv-benchmark "mgbench-zippy-mg-cpu1-hpc-rodinia:*"
    export NUM_CPU_CORES=2
    mx asv-benchmark "mgbench-zippy-mg-cpu2-hpc-rodinia:*"
    # export NUM_CPU_CORES=4
    # mx asv-benchmark "mgbench-zippy-mg-cpu4-hpc-rodinia:*"
    # export NUM_CPU_CORES=6
    # mx asv-benchmark "mgbench-zippy-mg-cpu6-hpc-rodinia:*"




##### Generate benchmark figures:

After a full benchmark, run the following script to generate figures base on your machine's results:


    $ cd $MEGAGUARDS_ROOT/zippy-megaguards
    $ ./figures.sh


Generated figures and summary pages will be in `zippy-megaguards/graphs/<YOUR_MACHINE_NAME>` after executing `figures.sh`


### Notes:

- We enabled Graal's optimization print trace to ensure that Graal is in use. Therefore, when executing a python
program you will see many printed lines starts with:


    [truffle] opt done         Constant@5ef04b5 <opt>                   |ASTSize ....

This means ZipPy's AST is being compiled using Graal JIT compiler.


- MegaGuards requires a single CPU OpenCL and a single GPU OpenCL devices to be fully tested and benchmarked.
