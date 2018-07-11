import os, sys
from subprocess import call

noerrors = True

env_path = 'PATH'
env_ld = 'LD_LIBRARY_PATH'
env_java = 'JAVA_HOME'
current_path = os.getcwd()

anaconda2_path = current_path + '/anaconda2'
anaconda3_path = current_path + '/anaconda3'
pypy3_path = current_path + '/pypy3-v5.10.0-linux64'
mx_path = current_path + '/mx'
jvmci_path = current_path + '/labsjdk1.8.0_151-jvmci-0.39'

list_files = os.listdir(current_path)
for f in list_files:
    if f.startswith('labsjdk1.8') and os.path.isdir(f):
        jvmci_path = current_path + f


env_vars = [
    [env_path, pypy3_path + ':$' + env_path],
    [env_path, anaconda3_path + ':$' + env_path],
    [env_path, anaconda2_path + ':$' + env_path],
    [env_path, mx_path + ':$' + env_path],
    [env_java, jvmci_path],
]

_ansi_color_table = { 'red' : '31', 'green' : '32', 'yellow' : '33', 'magenta' : '35' }
def colorize(msg, color='red', bright=True, stream=sys.stderr):
    if not msg:
        return None
    code = _ansi_color_table.get(color, None)

    if bright:
        code += ';1'
    color_on = '\033[' + code + 'm'
    if not msg.startswith(color_on):
        isUnix = sys.platform.startswith('linux') or sys.platform in ['darwin', 'freebsd']
        if isUnix and hasattr(stream, 'isatty') and stream.isatty():
            return color_on + msg + '\033[0m'
    return msg

def print_wait(wait_time):
    for remaining in range(wait_time, 0, -1):
        sys.stdout.write("\r")
        sys.stdout.write("[  ...  ] continue in {:2d} seconds".format(remaining))
        sys.stdout.flush()
        time.sleep(1)
    print('')

def print_ok(msg):
    ok = colorize('  OK   ', 'green')
    print('[' + ok + '] ' + msg)

def print_progress(msg):
    print('[  ...  ] ' + msg)

def print_error(msg):
    err = colorize(' ERROR ', 'red')
    print('[' + err + '] ' + msg)

def print_warn(msg):
    w = colorize('WARNING', 'magenta')
    print('[' + w + '] ' + msg)

def print_info(msg):
    i = colorize(' INFO  ', 'yellow')
    print('[' + i + '] ' + msg)

def execute_shell(cmd):
    global noerrors
    print_progress('Executing: %s' % ' '.join(map(str, cmd)))
    retcode = call(cmd)
    if retcode != 0:
        print_error('Please fix this and re-run this script')
        noerrors = False
        # exit(1)
    else:
        print_ok('Success')


print_info('This script is compatible with Ubuntu 16.04')

cmd = ["sudo", "apt-get", "install", "build-essential"]
execute_shell(cmd)

cmd = ["sudo", "apt-get", "install", "git"]
execute_shell(cmd)

cmd = ["sudo", "apt-get", "install", "wget"]
execute_shell(cmd)

cmd = ["sudo", "apt-get", "install", "curl"]
execute_shell(cmd)

cmd = ["sudo", "apt-get", "install", "ocl-icd-opencl-dev"]
execute_shell(cmd)

cmd = ["sudo", "apt-get", "install", "clinfo"]
execute_shell(cmd)

print_progress('Trying to locate anaconda2 at %s' % anaconda2_path)
if not os.path.isfile(anaconda2_path + '/LICENSE.txt'):
    print_progress('Could not find anaconda2. Trying to download it')
    if not os.path.isfile("Anaconda2-5.1.0-Linux-x86_64.sh"):
        cmd = ["wget", "https://repo.continuum.io/archive/Anaconda2-5.1.0-Linux-x86_64.sh"]
        execute_shell(cmd)
    cmd = ["chmod", "+x", "Anaconda2-5.1.0-Linux-x86_64.sh"]
    execute_shell(cmd)
    cmd = ["./Anaconda2-5.1.0-Linux-x86_64.sh", "-b", "-p", anaconda2_path]
    execute_shell(cmd)
else:
    print_ok("found!")

print_progress('Trying to locate anaconda3 at %s' % anaconda3_path)
if not os.path.isfile(anaconda3_path + '/LICENSE.txt'):
    print_progress('Could not find anaconda3. Trying to download it')
    if not os.path.isfile("Anaconda3-4.4.0-Linux-x86_64.sh"):
        cmd = ["wget", "https://repo.continuum.io/archive/Anaconda3-4.4.0-Linux-x86_64.sh"]
        execute_shell(cmd)
    cmd = ["chmod", "+x", "Anaconda3-4.4.0-Linux-x86_64.sh"]
    execute_shell(cmd)
    cmd = ["./Anaconda3-4.4.0-Linux-x86_64.sh", "-b", "-p", anaconda3_path]
    execute_shell(cmd)
else:
    print_ok("found!")


print_progress('Trying to locate PyPy3 at %s' % pypy3_path)
if not os.path.isfile(pypy3_path + '/LICENSE'):
    print_progress('Could not find PyPy3. Trying to download it')
    if not os.path.isfile("pypy3-v5.10.0-linux64.tar.bz2"):
        cmd = ["wget", "https://bitbucket.org/pypy/pypy/downloads/pypy3-v5.10.0-linux64.tar.bz2"]
        execute_shell(cmd)
    cmd = ["tar", "-xjf", "pypy3-v5.10.0-linux64.tar.bz2"]
    execute_shell(cmd)
else:
    print_ok("found!")


print_progress('Trying to locate mx at %s' % mx_path)
if not os.path.isfile(mx_path + '/LICENSE'):
    print_progress('Could not find mx. Trying to download it')
    cmd = ["git", "clone", "https://github.com/graalvm/mx.git"]
    execute_shell(cmd)
else:
    print_ok("found!")


print_progress('Trying to locate JDK with JVMCI at %s' % jvmci_path)
if not os.path.isfile(jvmci_path + '/LICENSE'):
    print_progress('Could not find JDK with JVMCI.')
    default_ver = 'labsjdk-8u151-jvmci-0.39-linux-amd64.tar.gz'
    value = default_ver
    if not os.path.isfile(default_ver):
        print_warn("JDK with JVMCI about 0.39 doesn't work. Please use 0.39 or earlier")
        print_info('Please download it from http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html')
        print_info('to this location: %s' % (current_path + '/' + default_ver))
        v = raw_input("Enter the name of the file [" + default_ver + "]: ")
        if v is not None or v != '':
            value = v
    cmd = ["tar", "-xzf", value]
    execute_shell(cmd)
else:
    print_ok("found!")


if noerrors:
    print_ok("Everything looks good")
else:
    print_error("Please fix above error before running MegaGuards")

print_info("Please add the following lines to your environment. e.g. ~/.bashrc, ~/zshrc")

print('')
for e in env_vars:
    print('%s=%s' % (e[0], e[1]))
print('')

print_info("Run: `source ~/.bashrc` or `source ~/.zshrc` before proceeding")
print_info("Then run the following commands:")
print_info(" $ cd zippy-megaguards")
print_info(" $ mx build")
print_info(" $ mx mg --init")
print_info(" $ mx mg --clinfo")
print_info(" $ mx mg --detect-ocl-platform")
print_info(" $ mx mg --simple-example")
print_info(" $ mx mg --check-requirements")

print_info("To run a sample benchmark: (it might take less than 1 Hour)")
print_info(" $ cd zippy-megaguards")
print_info(" $ ./run-sample-bench.sh")


print_info("To run a complete benchmark: (it might take more than 48 Hours)")
print_info(" $ cd zippy-megaguards")
print_info(" $ ./run.sh")


print_info("To generate figures and summary page.")
print_info(" $ cd zippy-megaguards")
print_info(" $ ./figures.sh")
