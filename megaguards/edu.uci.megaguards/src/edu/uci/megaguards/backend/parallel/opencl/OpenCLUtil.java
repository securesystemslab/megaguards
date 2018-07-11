/*
 * Copyright (c) 2018, Regents of the University of California
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.uci.megaguards.backend.parallel.opencl;

import java.util.Arrays;
import java.util.HashMap;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.analysis.parallel.exception.CompilationException;
import edu.uci.megaguards.log.MGLog;

public class OpenCLUtil {

    private final static long MAXLOCALSIZE = 1024;

    private final static HashMap<String, Long[]> kernelDeviceLocalLimit = new HashMap<>();

    public static long[] getGlobalWorkSize(long[][] ranges, int levels) {
        final long globalWorkSize1D = (long) Math.ceil((ranges[0][1] - ranges[0][0]) / (double) ranges[0][2]);

        if (levels == 1)
            return new long[]{globalWorkSize1D};

        final long globalWorkSize2D = (long) Math.ceil((ranges[1][1] - ranges[1][0]) / (double) ranges[1][2]);

        if (levels == 2)
            return new long[]{globalWorkSize1D, globalWorkSize2D};

        final long globalWorkSize3D = (long) Math.ceil((ranges[2][1] - ranges[2][0]) / (double) ranges[2][2]);

        return new long[]{globalWorkSize1D, globalWorkSize2D, globalWorkSize3D};
    }

    // XXX: currently support single dimension. This is mainly for reduction which is a single
    // dimension.
    public static void setWorkloadSizes(OpenCLDevice device, long[][] ranges, int levels, long[] globalSize, long[] localSize, long[] groupSize) {
        long deviceMaxLocalSize = Math.min(device.getMaxWorkItemSizes()[0], MAXLOCALSIZE);
        long deviceMaxGroupSize = Math.min(device.getMaxWorkGroupSize(), MAXLOCALSIZE);
        long globalWorkSize = getGlobalWorkSize(ranges, levels)[0];
        if (globalWorkSize <= deviceMaxLocalSize) {
            localSize[0] = globalSize[0] = (long) Math.pow(2, Math.floor(Math.log10(globalWorkSize) / Math.log10(2)));
            groupSize[0] = 1;
        } else {
            long g = (long) Math.floor(globalWorkSize / deviceMaxLocalSize);
            if (g > deviceMaxGroupSize) {
                groupSize[0] = deviceMaxGroupSize;
            } else {
                groupSize[0] = g;
            }
            localSize[0] = deviceMaxLocalSize;
            globalSize[0] = localSize[0] * groupSize[0];
        }
    }

    private static long[] getFactors(long globalSize, long maxLocalSize, Long maxGroupSize) {
        final long factors[] = new long[maxGroupSize.intValue()];
        int i = 0;

        for (long f = 1; f <= maxLocalSize; f++) {
            if ((globalSize % f) == 0) {
                factors[i++] = f;
            }
        }

        return Arrays.copyOf(factors, i);
    }

    public static void reportLocalSizeFailure(org.jocl.cl_kernel kernel, OpenCLDevice device, long[] localSize) throws CompilationException {
        String key = kernel.hashCode() + "" + device.hashCode();
        if (localSize == null)
            throw CompilationException.INSTANCE.message("Kernel execution failed");
        long currentLocalSize = localSize[0] * localSize[1] * localSize[2];
        if (currentLocalSize == 1)
            throw CompilationException.INSTANCE.message("Kernel execution failed");
        kernelDeviceLocalLimit.put(key, new Long[]{Math.max(localSize[0] - 1, 1), Math.max(localSize[1] - 1, 1), Math.max(localSize[2] - 1, 1)});
    }

    public static long[] createLocalSize(org.jocl.cl_kernel kernel, OpenCLDevice device, long[] globalSize, int levels, MGLog log) {
        final Long[] localLimit;
        if (kernelDeviceLocalLimit.containsKey(kernel.hashCode() + "" + device.hashCode())) {
            localLimit = kernelDeviceLocalLimit.get(kernel.hashCode() + "" + device.hashCode());
        } else {
            localLimit = new Long[]{MAXLOCALSIZE, MAXLOCALSIZE, MAXLOCALSIZE};
        }

        String s = String.format("Range: Dim: %d", levels);
        long[] localSize = null;
        if (levels == 1) {
            localSize = createLocalSize1D(device, globalSize, localLimit);
            s += String.format("\tGlobal: %d", globalSize[0]);
            if (localSize != null)
                s += String.format("\tLocal: %d", localSize[0]);
        }

        if (levels == 2) {
            localSize = createLocalSize2D(device, globalSize, localLimit);
            s += String.format("\tGlobal: %d x %d", globalSize[0], globalSize[1]);
            if (localSize != null)
                s += String.format("\tLocal: %d x %d", localSize[0], localSize[1]);
        }
        if (levels == 3) {
            localSize = createLocalSize3D(device, globalSize, localLimit);
            s += String.format("\tGlobal: %d x %d x %d", globalSize[0], globalSize[1], globalSize[2]);
            if (localSize != null)
                s += String.format("\tLocal: %d x %d x %d", localSize[0], localSize[1], localSize[2]);
        }
        if (MGOptions.Backend.Debug > 0) {
            log.println(s);
        }

        return localSize;
    }

    public static long[] createLocalSize1D(OpenCLDevice device, long[] globalSize, Long[] LocalLimit) {
        long[] localSize = new long[]{1, 1, 1};

        if (globalSize[0] == 0) {
            return localSize;
        }

        final long[] factors = getFactors(globalSize[0], Math.min(device.getMaxWorkItemSizes()[0], LocalLimit[0]), device.getMaxWorkGroupSize());

        localSize[0] = factors[factors.length - 1];

        boolean valid = ((localSize[0] > 0) && (localSize[0] <= Math.min(device.getMaxWorkItemSizes()[0], LocalLimit[0])) && (localSize[0] <= device.getMaxWorkGroupSize()) &&
                        ((globalSize[0] % localSize[0]) == 0));

        return valid ? localSize : null;
    }

    public static long[] createLocalSize2D(OpenCLDevice device, long[] globalSize, Long[] LocalLimit) {
        long[] localSize = new long[]{1, 1, 1};

        final long[] widthFactors = getFactors(globalSize[0], Math.min(device.getMaxWorkItemSizes()[0], LocalLimit[0]), device.getMaxWorkGroupSize());
        final long[] heightFactors = getFactors(globalSize[1], Math.min(device.getMaxWorkItemSizes()[1], LocalLimit[1]), device.getMaxWorkGroupSize());

        localSize[0] = 1;
        localSize[1] = 1;
        long max = 1;
        long perimeter = 0;

        for (final long w : widthFactors) {
            for (final long h : heightFactors) {
                final long size = w * h;
                if (size > device.getMaxWorkGroupSize()) {
                    break;
                }

                if (size > max) {
                    max = size;
                    perimeter = w + h;
                    localSize[0] = w;
                    localSize[1] = h;
                } else if (size == max) {
                    final long localPerimeter = w + h;
                    if (localPerimeter < perimeter) {// is this the shortest perimeter so far
                        perimeter = localPerimeter;
                        localSize[0] = w;
                        localSize[1] = h;
                    }
                }
            }
        }

        boolean valid = ((localSize[0] > 0) && (localSize[1] > 0) && (localSize[0] <= Math.min(device.getMaxWorkItemSizes()[0], LocalLimit[0])) &&
                        (localSize[1] <= Math.min(device.getMaxWorkItemSizes()[1], LocalLimit[1])) &&
                        ((localSize[0] * localSize[1]) <= device.getMaxWorkGroupSize()) && ((globalSize[0] % localSize[0]) == 0) && ((globalSize[1] % localSize[1]) == 0));

        return valid ? localSize : null;
    }

    public static long[] createLocalSize3D(OpenCLDevice device, long[] globalSize, Long[] LocalLimit) {
        long[] localSize = new long[]{1, 1, 1};

        final long[] widthFactors = getFactors(globalSize[0], Math.min(device.getMaxWorkItemSizes()[0], LocalLimit[0]), device.getMaxWorkGroupSize());
        final long[] heightFactors = getFactors(globalSize[1], Math.min(device.getMaxWorkItemSizes()[1], LocalLimit[1]), device.getMaxWorkGroupSize());
        final long[] depthFactors = getFactors(globalSize[2], Math.min(device.getMaxWorkItemSizes()[2], LocalLimit[2]), device.getMaxWorkGroupSize());

        localSize[0] = 1;
        localSize[1] = 1;
        localSize[2] = 1;

        long max = 1;
        long perimeter = 0;

        for (final long w : widthFactors) {
            for (final long h : heightFactors) {
                for (final long d : depthFactors) {
                    final long size = w * h * d;
                    if (size > device.getMaxWorkGroupSize()) {
                        break;
                    }

                    if (size > max) {
                        max = size;
                        perimeter = w + h + d;
                        localSize[0] = w;
                        localSize[1] = h;
                        localSize[2] = d;
                    } else if (size == max) {
                        final long localPerimeter = w + h + d;
                        if (localPerimeter < perimeter) {
                            perimeter = localPerimeter;
                            localSize[0] = localSize[1] = w;
                            localSize[2] = d;
                        }
                    }
                }
            }
        }

        boolean valid = ((localSize[0] > 0) && (localSize[1] > 0) && (localSize[2] > 0) && ((localSize[0] * localSize[1] * localSize[2]) <= device.getMaxWorkGroupSize()) &&
                        (localSize[0] <= Math.min(device.getMaxWorkItemSizes()[0], LocalLimit[0])) && (localSize[1] <= Math.min(device.getMaxWorkItemSizes()[1], LocalLimit[1])) &&
                        (localSize[2] <= Math.min(device.getMaxWorkItemSizes()[2], LocalLimit[2])) &&
                        ((globalSize[0] % localSize[0]) == 0) && ((globalSize[1] % localSize[1]) == 0) && ((globalSize[2] % localSize[2]) == 0));

        return valid ? localSize : null;
    }
}
