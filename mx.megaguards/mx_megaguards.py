from argparse import ArgumentParser
import re
import time
import os
import sys
import zipfile
import shutil
import subprocess
import mx

_suite = mx.suite('megaguards')
_mx_graal = mx.suite("compiler", fatalIfMissing=False)

os_name = sys.platform if sys.platform != 'linux2' else 'linux'
lib_path = _suite.dir + os.sep + 'lib' + os.sep
megaguards_benchmarks_path = _suite.dir + os.sep + 'megaguards' + os.sep + 'megaguards-benchmarks-suite'
megaguards_dataset_path =  _suite.dir + os.sep + 'dataset' + os.sep
megaguards_dataset_benchmark_path =  megaguards_dataset_path + 'benchmark'
megaguards_dataset_tests_path =  megaguards_dataset_path + 'test'
if not os.path.exists(lib_path):
    os.mkdir(lib_path)
downloads_compressed_path = lib_path + 'downloads'
if not os.path.exists(downloads_compressed_path):
    os.mkdir(downloads_compressed_path)
downloads_compressed_path += os.sep

megaguards_downloads = {
    "SUPPORTED_OS": ['linux'], # OSX and windows aren't supported yet.

    "LIBS" : {
        "ATHENAPET_LD_LIBRARY" : {
            "linux" : {
                "name": "libjniAthenaPetJNI",
                "url" : "https://www.dropbox.com/s/no3nkqixipbc7qx/AthenaPet.zip?dl=1",
                "sha1": "1f04bf2fa7d99b496cf25e3103e720386157ce33",
                "compressed" : True,
                "suffix": ".so"
            },
            "darwin" : {
                "name": "libjniAthenaPetJNI",
                "url" : "",
                "sha1": "",
                "compressed" : True,
                "suffix": ".dylib"
            }
        },
    },

    "DATASET" : {
        "rodinia-test-dataset" : {
            "url" : "https://www.dropbox.com/s/94sp0kdd7g28f9g/rodinia-test.zip?dl=1",
            "sha1": "b4a22305320179c62d187fd99960f64abcb5f9bd",
            "path" : megaguards_dataset_tests_path,
        },
        "rodinia-benchmark-dataset" : {
            "url" : "https://www.dropbox.com/s/hhyz0t0sg4vq0zq/rodinia.zip?dl=1",
            "sha1": "78915b49dd621f6500073aa97b8b408a478c8894",
            "path" : megaguards_dataset_benchmark_path,
        },
    }

}

def _extract_megaguards_internal_options(args):
    internal = []
    noneInternal = []
    for arg in args:
        if arg == '--zippy-memory':
            internal += ['-javaagent:' + mx.library("JAMM").path]

        else:
            noneInternal += [arg]

    return internal, noneInternal

def print_ok(msg):
    ok = mx.colorize('   OK  ', 'green')
    print('[' + ok + '] ' + msg)

def print_progress(msg):
    print('[  ...  ] ' + msg)

def print_error(msg):
    err = mx.colorize(' ERROR ', 'red')
    print('[' + err + '] ' + msg)

def print_warn(msg):
    w = mx.colorize('WARNING', 'magenta')
    print('[' + w + '] ' + msg)

def print_info(msg):
    i = mx.colorize('  INFO ', 'yellow')
    print('[' + i + '] ' + msg)

def print_status(is_ok, msg):
    if is_ok:
        print_ok(msg)
    else:
        print_error(msg)

def update_env_file(envVar, value):
    if value != '' and not os.environ.get(envVar):
        _lines = []
        if not os.path.exists(os.path.join(_suite.mxDir, 'env')):
            with open(os.path.join(_suite.mxDir, 'env'),'w') as fp:
                fp.write("# MegaGuards environment variables\n")

        with open(os.path.join(_suite.mxDir, 'env'),'r') as fp:
            for line in fp:
                _lines += [line]
        added = False
        lines = []
        for line in _lines:
            if '=' in line and '#' not in line:
                line = line.split('=')
                var = line[0]
                varpath = line[1]
                if envVar == var:
                    if varpath != value:
                        var = '# ' + var
                    else:
                        added = True
                lines += [var + '=' + varpath]
            else:
                lines += [line]

        if not added:
            lines += [envVar + "=" + value + '\n']
            print_progress("adding %s to %s" % (value, _suite.mxDir + os.sep + 'env'))

        with open(os.path.join(_suite.mxDir, 'env'),'w') as fp:
            for line in lines:
                fp.write(line)


def unpack_zip(name, unpack_dir, zip_file_path):
    zip_ref = zipfile.ZipFile(zip_file_path,'r')
    uncompress_size = sum((f.file_size for f in zip_ref.infolist()))
    try:
        print_progress("Unpacking %s to %s.." % (name, unpack_dir))
        extracted_size = 0
        for f in zip_ref.infolist():
            extracted_size += f.file_size
            progress = '' if extracted_size == -1 else '(' + str( extracted_size * 100 / uncompress_size) + '%)'
            msg = '%s (%s bytes) %s' % (f.filename,str(f.file_size), progress)
            msg = msg + (' ' * (0 if len(msg) >= 65 else 65 - len(msg)))
            sys.stdout.write('\r %s' % msg)
            zip_ref.extract(f, unpack_dir)

        sys.stdout.write('\n')
        zip_ref.close()
    except:
        shutil.rmtree(unpack_dir)
        zip_ref.close()
        raise

    return True

def stamp_unpacked(name, unpack_dir, sha1):
    output = open(unpack_dir + os.sep + '.' + name,'w')
    output.write(sha1)
    output.close()

def download_file(name, url, unpack_dir, is_compressed, sha1sum, suffix=''):
    if not is_compressed:
        filepath = unpack_dir + name + suffix
        if not os.path.exists(filepath) or mx.sha1OfFile(filepath) != sha1sum:
            mx.download(filepath, [url])
    else:
        zip_file_path = downloads_compressed_path + name + '.zip'
        if not os.path.exists(zip_file_path) or mx.sha1OfFile(zip_file_path) != sha1sum:
            mx.download(zip_file_path,[url])
        unpack_zip(name, unpack_dir, zip_file_path)

def is_lib_exists(name, sha1sum, suffix, is_compressed):
    path = lib_path + name + suffix
    if is_compressed:
        path = downloads_compressed_path + name + '.zip'

    lib_exists = os.path.exists(path)
    lib_exists = lib_exists and mx.sha1OfFile(path) == sha1sum
    return lib_exists

def is_dataset_exists(name, sha1sum, stamp_file):
    path = downloads_compressed_path + name + '.zip'
    dataset_exists = os.path.exists(stamp_file)
    if not dataset_exists:
        return dataset_exists
    dataset_exists = dataset_exists and os.path.exists(path)
    dataset_exists = dataset_exists and mx.sha1OfFile(path) == sha1sum
    with open(stamp_file) as stamp:
        saved_sha1 = stamp.readline()
        dataset_exists = dataset_exists and saved_sha1 == sha1sum
    return dataset_exists

def get_megaguards_lib(envVar, force=False, check_only=False):
    lib = megaguards_downloads['LIBS'][envVar][os_name]
    name = lib['name']
    url = lib['url']
    sha1sum = lib['sha1']
    suffix = lib['suffix']
    is_compressed = lib['compressed']
    unpack_dir = lib_path
    is_exist = is_lib_exists(name, sha1sum, suffix, is_compressed)
    if check_only:
        return is_exist
    if not is_exist or force:
        download_file(name, url, unpack_dir, is_compressed, sha1sum, suffix)
        is_exist = True

    if is_exist:
        update_env_file(envVar, unpack_dir + name + suffix)

    return is_exist

def get_megaguards_dataset(dataset_name, force=False, check_only=False):
    dataset = megaguards_downloads['DATASET'][dataset_name]
    name = dataset_name
    url = dataset['url']
    sha1sum = dataset['sha1']
    unpack_dir = dataset['path']
    is_exist = is_dataset_exists(name, sha1sum, unpack_dir + os.sep + '.' + name)
    if check_only:
        return is_exist
    if not is_exist or force:
        download_file(name, url, unpack_dir, True, sha1sum)
        stamp_unpacked(name, unpack_dir, sha1sum)
        is_exist = True

    return is_exist

def get_megaguards_envpath(envVar, path, force=False, check_only=False):
    is_exist = os.environ.get(envVar) != None
    if check_only:
        return is_exist
    if not is_exist or force:
        update_env_file(envVar, path)
        is_exist = True

    return is_exist


opencl_err = """
MegaGuards could not locate a valid OpenCL device of type {0}. Please check the following:
    * {0} supports double presision operation (check {0} spec and query 'clinfo')
    * {0} driver that includes OpenCL library (check '*.icd' files in '/etc/OpenCL/vendor/')"""

def check_megaguards(device='GPU', verbose=False, cmd=['mx', 'python'], testprogram='check_mg.py'):
    megaguards_opt = ['--mg-target=' + device.lower(), '--mg-log=eyxd', '--mg-target-threshold=1']
    check_python_program = [_suite.dir + os.sep + 'tests' + os.sep + testprogram]
    n = 3
    for t in range(n):
        out = mx.OutputCapture()
        _out = out if not verbose else mx.TeeOutputCapture(out)
        out_err = mx.OutputCapture()
        _out_err = out if not verbose else mx.TeeOutputCapture(out_err)
        print_progress('Testing OpenCL device %s accessibility' % device)
        retcode = mx.run(cmd + check_python_program + megaguards_opt, out=_out, err=_out_err, nonZeroIsFatal=False)
        if retcode == 0:
            break
        else:
            print("Execution failed.. retry %d of %d" % (t+1, n))

    successRe = r"Execution Target:.+" + device + r""
    if not re.search(successRe, out.data, re.MULTILINE):
        print_error(opencl_err.format(device))
        return False
    else:
        print_ok("OpenCL device {0} has been detected!".format(device))
        return True

def get_megaguards_benchmark_suite(force=False, check_only=False, verbose=False):
    is_exist = os.path.exists(_suite.dir + megaguards_benchmarks_path + os.sep + '.git')
    if check_only:
        return is_exist
    if not is_exist or force:
        print_progress('Importing MegaGuards benchmarks suite')
        out = mx.OutputCapture()
        _out = out if not verbose else mx.TeeOutputCapture(out)
        mx.run(['git', 'submodule', 'update'], out=_out, nonZeroIsFatal=True)
        is_exist = True

    return is_exist

def get_megaguards_home_dir(force=False, check_only=False):
    return get_megaguards_envpath("MG_HOME", _suite.dir, force, check_only)

def get_megaguards_build_dir(force=False, check_only=False):
    return get_megaguards_envpath("MG_BUILD_DIR", _suite.get_output_root(), force, check_only)

def get_megaguards_polyhedral_ld(force=False, check_only=False):
    return get_megaguards_lib('ATHENAPET_LD_LIBRARY', force, check_only)


def get_megaguards_test_dataset(force=False, check_only=False):
    return get_megaguards_dataset('rodinia-test-dataset', force, check_only)

def get_megaguards_benchmark_dataset(force=False, check_only=False):
    get_megaguards_benchmark_suite(force, check_only)
    return get_megaguards_dataset('rodinia-benchmark-dataset', force, check_only)

def get_jdk():
    if _mx_graal:
        tag = 'jvmci'
    else:
        tag = None
    return mx.get_jdk(tag=tag)

def run_mg_internal(args, verbose=False, extraVmArgs=None, env=None, jdk=None, **kwargs):
    vmArgs, mgArgs = mx.extract_VM_args(args)
    vmArgs = ['-cp', mx.classpath(["edu.uci.megaguards"])]
    vmArgs.append("edu.uci.megaguards.shell.MGMain")
    if not jdk:
        jdk = get_jdk()
    out = mx.OutputCapture()
    _out = out if not verbose else mx.TeeOutputCapture(out)
    out_err = mx.OutputCapture()
    _out_err = out if not verbose else mx.TeeOutputCapture(out_err)
    n = 3
    for t in range(n):
        retcode = mx.run_java(vmArgs + [mgArgs], out=_out, err=_out_err, jdk=jdk, **kwargs)
        if retcode == 0:
            break
    return out.data

def check_polyhedral_mg(verbose=False):
    success = 'Polyhedral test is operational'
    failure = 'Polyhedral test FAILED!'
    output_data = run_mg_internal('polyhedral-test')
    result = output_data.split('\n')
    for text in result:
        if success in text:
            print_ok(success)
            break
        if failure in text:
            print_error(failure)
            break



def clinfo_mg(verbose=False):
    warn_minimum_mem = {
    "CPU" : ["16 GB", 16 * (10**9)],
    "GPU" : ["6 GB",  6  * (10**9)]
    }
    if verbose:
        run_mg_internal('clinfo', verbose)

    try:
        import json
    except:
        print_error('missing json module')
        return

    output_json = run_mg_internal('clinfo-json')
    clinfo_data = json.loads(output_json)
    num_devices = clinfo_data["Total number of OpenCL devices"]
    num_oclcpus = clinfo_data["Number of OpenCL CPU devices"]
    num_oclgpus = clinfo_data["Number of OpenCL GPU devices"]
    print_progress('Number of OpenCL devices: %d [%d CPU(s)] [%d GPU(s)]' % (num_devices, num_oclcpus, num_oclgpus))
    if num_devices > 0:
        cpu = None
        if num_oclcpus > 0:
            print_progress("\tCPUs:");
            for cpu in clinfo_data["CPUs"]:
                s  = '\t\t' + cpu + ' '
                s += '(' + clinfo_data["CPUs"][cpu]["Max clock frequency (MHz)"] + ' MHz)' + ' '
                s += '(' + '%d' % clinfo_data["CPUs"][cpu]["Max compute units"] + ' CU)' + ' '
                s += '(' + clinfo_data["CPUs"][cpu]["Global memory size (h)"] + ')' + ' '
                s += 'v' + clinfo_data["CPUs"][cpu]["Driver version"]
                print_progress(s)
                if int(clinfo_data["CPUs"][cpu]["Global memory size (Byte)"]) < warn_minimum_mem["CPU"][1]:
                    print_warn("%s memory is %s < %s" % (cpu, clinfo_data["CPUs"][cpu]["Global memory size (h)"], warn_minimum_mem["CPU"][0]))
                    print_warn("This might cause some benchmarks to fallback to Truffle mode")
        gpu = None
        if num_oclgpus > 0:
            print_progress("\tGPUs:");
            for gpu in clinfo_data["GPUs"]:
                s  = '\t\t' + gpu + ' '
                s += '(' + clinfo_data["GPUs"][gpu]["Max clock frequency (MHz)"] + ' MHz)' + ' '
                s += '(' + '%d' % clinfo_data["GPUs"][gpu]["Max compute units"] + ' CU)' + ' '
                s += '(' + clinfo_data["GPUs"][gpu]["Global memory size (h)"] + ')' + ' '
                s += 'v' + clinfo_data["GPUs"][gpu]["Driver version"]
                print_progress(s)
                if int(clinfo_data["GPUs"][gpu]["Global memory size (Byte)"]) < warn_minimum_mem["GPU"][1]:
                    print_warn("%s memory is %s < %s" % (gpu, clinfo_data["GPUs"][gpu]["Global memory size (h)"], warn_minimum_mem["GPU"][0]))
                    print_warn("This might cause some benchmarks to fallback to Truffle mode")

        print_ok('MegaGuards detected OpenCL device(s)')
        if num_oclgpus != 1 and num_oclcpus != 1:
            print_error('MegaGuards benchmarking might not work properly there should be one OpenCL CPU and one GPU')
            print_error('  The number of OpenCL CPU devices should be "1" and OpenCL GPU devices should be "1"')
        else:
            return cpu, gpu

    else:
        print_error('MegaGuards did not find any OpenCL device')
    return None, None

def get_megaguards_junit_status(verbose=False):
    is_ok = get_megaguards_home_dir(check_only=True)
    is_ok = is_ok and get_megaguards_build_dir(check_only=True)
    is_ok = is_ok and get_megaguards_polyhedral_ld(check_only=True)
    is_ok = is_ok and get_megaguards_test_dataset(check_only=True)
    if is_ok:
        n = 3
        for t in range(n):
            out = mx.OutputCapture()
            _out = out if not verbose else mx.TeeOutputCapture(out)
            out_err = mx.OutputCapture()
            _out_err = out if not verbose else mx.TeeOutputCapture(out_err)
            print_progress("Performing MegaGuards (core) junit tests.. (note: run 'mx junit-mg' for complete MegaGuards junit tests)")
            retcode = mx.run(['mx', 'junit-mg-core'], out=_out, err=_out_err, nonZeroIsFatal=False)
            if retcode == 0:
                break
            else:
                print_progress("Test failed.. retry %d of %d" % (t+1, n))
        if retcode == 0:
            print_ok('MegaGuards core junit tests')
        else:
            print_warn('MegaGuards core junit tests encountered some errors.')

        is_ok = is_ok and retcode == 0

    return is_ok

def check_benchmark_requirements(verbose=False):
    programs_version = {
    'CPython2'  : ['python', '--version',  '2.7.14', '(Download URL: "https://repo.continuum.io/archive/Anaconda2-5.1.0-Linux-x86_64.sh")'],
    'CPython3'  : ['python3', '--version', '3.5.3',  '(Download URL: "https://repo.continuum.io/archive/Anaconda3-4.4.0-Linux-x86_64.sh")'],
    'PyPy3'     : ['pypy3'  , '--version', '5.10.0', '(Download URL: "https://bitbucket.org/pypy/pypy/downloads/pypy3-v5.10.0-linux64.tar.bz2")'],
    'GCC'       : ['gcc'    , '--version', '5.4.1',  '(Run: "sudo apt-get install gcc-5")']
    }
    out = mx.OutputCapture()
    _out = out if not verbose else mx.TeeOutputCapture(out)
    out_err = mx.OutputCapture()
    _out_err = out if not verbose else mx.TeeOutputCapture(out_err)
    for p in programs_version:
        a = programs_version[p]
        retcode = mx.run([a[0], a[1]], out=_out, err=_out_err, nonZeroIsFatal=False)
        if retcode != 0:
            print_error('%s was not found or not in $PATH. %s' % (p, a[3]))
        elif a[2] in out.data:
            print_ok('%s v%s exists' % (p, a[2]))
        else:
            print_warn('%s exists but mis-match the recommended v%s' % (p, a[2]))

    try:
        import matplotlib
        import matplotlib.pyplot as plt
        import matplotlib.ticker as ticker
    except:
        print_error('matplotlib was not found (Run: "conda install matplotlib")')

    try:
        import numpy
    except:
        print_error('numpy was not found (Run: "conda install numpy")')

    try:
        from scipy import stats
    except:
        print_error('scipy was not found (Run: "conda install scipy")')

    try:
        from functools import reduce
    except:
        print_error('reduce function was not found')

    try:
        import csv
    except:
        print_error('missing csv module')

    try:
        import xml
    except:
        print_error('missing csv module')

    try:
        import json
    except:
        print_error('missing json module')

def _get_out_outerr(verbose=False):
    out = mx.OutputCapture()
    _out = out if not verbose else mx.TeeOutputCapture(out)
    out_err = mx.OutputCapture()
    _out_err = out_err if not verbose else mx.TeeOutputCapture(out_err)
    return _out, _out_err

def find_opencl_device_platform(verbose=False):
    from mx_mg_conf import envoclcpu, envoclgpu
    envoclcpu_set = False
    if envoclcpu in os.environ:
        print_info('%s=%s already set in the "env" file' % (envoclcpu, os.environ[envoclcpu]))
        envoclcpu_set = True

    envoclgpu_set = False
    if envoclgpu in os.environ:
        print_info('%s=%s already set in the "env" file' % (envoclgpu, os.environ[envoclgpu]))
        envoclgpu_set = True

    from mx_zippy_bench_param import mg_opencl_bench_paths
    ocl_mm_path = mg_opencl_bench_paths['mm']
    out, out_err = _get_out_outerr(verbose)
    if not os.path.isfile(ocl_mm_path + 'mm'):
        retcode = mx.run(['make'], out=out, err=out_err, cwd=ocl_mm_path, nonZeroIsFatal=True)
    oclcpu, oclgpu = clinfo_mg()
    if not oclcpu or not oclgpu:
        print_error("Please fix this before running the benchmarks")

    if envoclcpu_set and envoclgpu_set:
        return

    if not envoclcpu_set:
        opencl_cpu_device_num = 1
        oclcpu_cmd = ["./mm", "64", "-t", "cpu", "-d"]
        found = False
        for p in range(5):
            out, out_err = _get_out_outerr(verbose)
            cmd = oclcpu_cmd + ['%d' % p]
            retcode = mx.run(cmd, out=out, err=out_err, cwd=ocl_mm_path, nonZeroIsFatal=False)
            output = out.data if not verbose else out.underlying.data
            if retcode == 0:
                if oclcpu in output or oclcpu[:5] in output or oclcpu[-5:] in output:
                    found = True
                    opencl_cpu_device_num = p
                    print_ok("%s OpenCL device platform index is %d" % (oclcpu, opencl_cpu_device_num))
                    break
        if found:
            update_env_file(envoclcpu, '%d' % opencl_cpu_device_num)
            os.environ[envoclcpu] = '%d' % opencl_cpu_device_num
        else:
            print_error('Unable to find the platform index for %s' % oclcpu)

    if not envoclgpu_set:
        opencl_gpu_device_num = 0
        oclgpu_cmd = ["./mm", "64", "-t", "gpu", "-d"]
        found = False
        for p in range(5):
            out, out_err = _get_out_outerr(verbose)
            cmd = oclgpu_cmd + ['%d' % p]
            retcode = mx.run(cmd, out=out, err=out_err, cwd=ocl_mm_path, nonZeroIsFatal=False)
            output = out.data if not verbose else out.underlying.data
            if retcode == 0:
                if oclgpu in output or oclgpu[:5] in output or oclgpu[-5:] in output:
                    found = True
                    opencl_gpu_device_num = p
                    print_ok("%s OpenCL device platform index is %d" % (oclgpu, opencl_gpu_device_num))
                    break

        if found:
            update_env_file(envoclgpu, '%d' % opencl_gpu_device_num)
            os.environ[envoclgpu] = '%d' % opencl_gpu_device_num
        else:
            print_error('Unable to find the platform index for %s' % oclcpu)

def run_simple_example(verbose=False):
    from mx_mg_conf import envoclcpu, envoclgpu
    from mx_zippy_bench_param import hpc_path, rodinia_path, mg_paths, mg_opencl_bench_paths
    examples = {
        "mm" : {
            "Python" : {
                "PATH"   : hpc_path + mg_paths['Python'],
                "exec"   : 'mm.py',
                "preArg" : [],
                "postArg": []
            },
            "OpenCL" : {
                "PATH"   : mg_opencl_bench_paths['mm'],
                "exec"   : 'mm',
                "preArg" : [],
                "postArg": []
            },
        },
        "lud" : {
            "Python" : {
                "PATH"   : rodinia_path + mg_paths['Python'],
                "exec"   : 'lud.py',
                "preArg" : ['-s'],
                "postArg": []
            },
            "OpenCL" : {
                "PATH"   : mg_opencl_bench_paths['lud'],
                "exec"   : 'lud',
                "preArg" : ['-s'],
                "postArg": []
            },
        },
    }

    example_args = ['64', '128', '256']
    find_opencl_device_platform(verbose)
    opencl_cpu_device_num = int(os.environ[envoclcpu])
    opencl_gpu_device_num = int(os.environ[envoclgpu])
    ordered_systems = ["ZipPy", "OpenCL-CPU", "MG-CPU", "OpenCL-GPU", "MG-GPU", "MG-Truffle", "PyPy3", "CPython3"]
    opencl_systems = {
    "OpenCL-CPU": [['./'], ['-t', 'cpu' , '-d', '%d' % opencl_cpu_device_num, '8']],
    "OpenCL-GPU": [['./'], ['-t', 'gpu' , '-d', '%d' % opencl_gpu_device_num, '8']],
    }
    python_systems = {
    "MG-Truffle": [['mx', 'python'], ['8', '--mg-target=truffle']],
    "MG-CPU": [['mx', 'python'], ['8', '--mg-target=cpu']],
    "MG-GPU": [['mx', 'python'], ['8', '--mg-target=gpu']],
    "ZipPy": [['mx', 'python'], ['8']],
    "PyPy3": [['pypy3'], ['8']],
    "CPython3": [['python3'], ['8']],
    }
    re_rule = r"^(?P<benchmark>[a-zA-Z0-9\.\-\_]+), Time,(?P<times>(\s[0-9]+(\.[0-9]+)?,)+)"
    # prog = re.compile(re_rule, re.MULTILINE)
    oclcpu, oclgpu = clinfo_mg()
    if not oclcpu or not oclgpu:
        print_error("Please fix this before running the benchmarks")

    results = {}
    print_info('Start time: ' + time.ctime())
    for e in examples:
        results[e] = {}
        out, out_err = _get_out_outerr(verbose)
        if not os.path.isfile(examples[e]['OpenCL']['PATH'] + examples[e]['OpenCL']['exec']):
            retcode = mx.run(['make'], out=out, err=out_err, cwd=examples[e]['OpenCL']['PATH'], nonZeroIsFatal=True)
        for arg in example_args:
            results[e][arg] = {}
            for ocl in opencl_systems:
                cwd=examples[e]['OpenCL']['PATH']
                cmd = []
                cmd += [opencl_systems[ocl][0][0] + examples[e]['OpenCL']['exec']]
                cmd += examples[e]['OpenCL']['preArg'] + [arg] + examples[e]['OpenCL']['postArg'] + opencl_systems[ocl][1]
                print_progress(' '.join(map(str, cmd)))
                out, out_err = _get_out_outerr(verbose)
                retcode = mx.run(cmd, out=out, err=out_err, cwd=cwd, nonZeroIsFatal=False)
                if retcode != 0:
                    results[e][arg][ocl] = -1.0
                    print_error('%s failed' % ocl)
                    continue
                output = out.data
                r = re.findall(re_rule, output, re.MULTILINE)
                t = r[0][1]
                t = t.replace(' ', '')[0:-1]
                times = [float(x) for x in t.split(',')]
                results[e][arg][ocl] = min(times)
            for pys in python_systems:
                cwd=examples[e]['Python']['PATH']
                cmd = []
                cmd += python_systems[pys][0] + [examples[e]['Python']['PATH'] + examples[e]['Python']['exec']]
                cmd += examples[e]['Python']['preArg'] + [arg] + examples[e]['Python']['postArg'] + python_systems[pys][1]
                print_progress(' '.join(map(str, cmd)))
                out, out_err = _get_out_outerr(verbose)
                for t in range(3):
                    retcode = mx.run(cmd, out=out, err=out_err, nonZeroIsFatal=False)
                    if retcode == 0:
                        break
                if retcode != 0:
                    results[e][arg][pys] = -1.0
                    print_error('%s failed' % pys)
                    continue
                output = out.data
                r = re.findall(re_rule, output, re.MULTILINE)
                t = r[0][1]
                t = t.replace(' ', '')[0:-1]
                times = [float(x) for x in t.split(',')]
                results[e][arg][pys] = min(times)
    print_info('End time: ' + time.ctime())

    format_pattern = "%-7s %-4s %-8s %-10s %-8s %-10s %-12s %-10s %-8s %-10s %-10s"
    print_progress(format_pattern % tuple(['bench', 'arg'] + ordered_systems + ['Unit']))
    for e in results:
        for arg in example_args:
            r = []
            for s in ordered_systems:
                r += [results[e][arg][s]]

            print_progress(format_pattern % tuple([e, arg] + r + ['(seconds)']))


def get_megaguards_setup(args):
    """run MegaGuards setup"""
    parser = ArgumentParser(prog='mx mg')
    parser.add_argument('--check-only', '-c', action='store_true', help='Check without making any change.')
    parser.add_argument('--force', '-f', action='store_true', help='Force the action and ignore local files.')
    parser.add_argument('--verbose', '-v', action='store_true', help='Print all suppressed output.')
    parser.add_argument('--init', '-i', action='store_true', help='Setup MegaGuards.')
    parser.add_argument('--init-all', action='store_true', help='Setup MegaGuards (including benchmark suite).')
    parser.add_argument('--polyhedral-ld', action='store_true', help='Setup Polyhedral analysis binary library (AthenaPet).')
    parser.add_argument('--check-polyhedral', action='store_true', help='Test Polyhedral analysis library.')
    parser.add_argument('--dataset-test', action='store_true', help='Download junit test dataset.')
    parser.add_argument('--dataset-benchmark', action='store_true', help='Download benchmark dataset.')
    parser.add_argument('--benchmark-suite', action='store_true', help='Download benchmark suite.')
    parser.add_argument('--clinfo', action='store_true', help='Print OpenCL devices information.')
    parser.add_argument('--test-gpu', '-t', action='store_true', help='Test GPU OpenCL device.')
    parser.add_argument('--test-cpu', action='store_true', help='Test CPU OpenCL device.')
    parser.add_argument('--check-requirements', action='store_true', help='Test benchmark requirements.')
    parser.add_argument('--simple-example', action='store_true', help='Run simple examples.')
    parser.add_argument('--detect-ocl-platform', action='store_true', help='Detect OpenCL devices platform index.')
    args = parser.parse_args(args)

    check_only = False
    if args.check_only:
        check_only = True

    force = False
    if args.force:
        force = True

    verbose = False
    if args.verbose:
        verbose = True

    init = False
    if args.init:
        init = True

    init_all = False
    if args.init_all:
        init = True
        init_all = True

    if os_name not in megaguards_downloads['SUPPORTED_OS']:
        print_error("MegaGuards, currently, only supports Linux x86 64-Bit")

    get_megaguards_home_dir(force, check_only)

    get_megaguards_build_dir(force, check_only)

    if args.polyhedral_ld or init:
        is_ok = get_megaguards_polyhedral_ld(force, check_only)
        print_status(is_ok, 'Setup Polyhedral analysis binary library (AthenaPet)')

    if args.check_polyhedral or init:
        check_polyhedral_mg(verbose)

    if args.dataset_test or init:
        is_ok = get_megaguards_test_dataset(force, check_only)
        print_status(is_ok, 'Download junit test dataset')

    if args.simple_example:
        run_simple_example(verbose)

    if args.clinfo or init:
        clinfo_mg(verbose)

    if args.test_gpu or init:
        check_megaguards('GPU', verbose)

    if args.test_cpu or init:
        check_megaguards('CPU', verbose)

    if args.detect_ocl_platform or init_all:
        find_opencl_device_platform(verbose)

    if args.check_requirements:
        check_benchmark_requirements(verbose)

    if init:
        is_ok = get_megaguards_junit_status(verbose)

    if args.benchmark_suite or init_all:
        is_ok = get_megaguards_benchmark_suite(force, check_only)
        print_status(is_ok, 'Download benchmark suite')

    if args.dataset_benchmark or init_all:
        is_ok = get_megaguards_benchmark_dataset(force, check_only)
        print_status(is_ok, 'Download benchmark dataset')


mx.update_commands(_suite, {
    # new commands
    'mg' : [get_megaguards_setup, ['options']],
})
