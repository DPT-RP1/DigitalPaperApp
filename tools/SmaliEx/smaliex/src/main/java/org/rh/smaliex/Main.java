/*
 * Copyright (C) 2014 Riddle Hsu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.rh.smaliex;

import org.jf.dexlib2.VersionMap;

import java.io.File;
import java.io.IOException;

public class Main {

    static void printUsage() {
        println("Easy oat2dex 0.90");
        println("Usage:");
        println(" java -jar oat2dex.jar [options] <action>");
        println("[options]");
        println(" Api level: -a <integer>");
        println(" Output folder: -o <folder path>");
        println(" Print detail : -v");
        println("<action>");
        println(" Get dex of boot(.oat) : boot <boot.oat/boot-folder>");
        println(" Get dex (de-optimize) : <oat/odex file> <boot-class-folder>");
        println("                         <vdex file>");
        println(" Get raw odex          : odex <oat/odex/vdex file>");
        println(" Get raw odex smali    : smali <oat/odex/vdex file>");
        println(" Deodex framework (exp): devfw [empty or path of /system/framework/]");
    }

    public static void main(String[] args) {
        try {
            mainImpl(args);
        } catch (IOException ex) {
            exit("Unhandled IOException: " + ex.getMessage());
        }
    }

    public static void mainImpl(String[] args) throws IOException {
        String outputPath = null;
        int apiLevel = VersionMap.NO_VERSION;
        if (args.length > 2) {
            String opt = args[0];
            while (opt.length() > 1 && opt.charAt(0) == '-') {
                int shift = 1;
                switch (opt.charAt(1)) {
                    case 'a':
                        try {
                            apiLevel = Integer.parseInt(args[1]);
                        } catch (NumberFormatException e) {
                            println("Invalid api level: " + args[1]);
                        }
                        shift = 2;
                        break;
                    case 'o':
                        outputPath = args[1];
                        shift = 2;
                        break;
                    case 'v':
                        LLog.VERBOSE = true;
                        break;
                    default:
                        println("Unrecognized option: " + opt);
                }
                final String[] newArgs = shiftArgs(args, shift);
                if (newArgs != args) {
                    args = newArgs;
                    if (newArgs.length < shift) {
                        break;
                    }
                } else {
                    break;
                }

                opt = args[0];
            }
        }
        if (args.length < 1) {
            printUsage();
            return;
        }

        final String cmd = args[0];
        if ("devfw".equals(cmd)) {
            DeodexFrameworkFromDevice.deOptimizeAuto(
                    args.length > 1 ? args[1] : null, outputPath);
            return;
        }

        if (args.length == 1) {
            checkExist(args[0]);
            OdexUtil.vdex2dex(args[0], outputPath);
        } else if (args.length == 2) {
            if ("boot".equals(cmd)) {
                checkExist(args[1]);
                OatUtil.bootOat2Dex(args[1], outputPath);
                return;
            }
            if ("odex".equals(cmd)) {
                File out = OdexUtil.extractOdex(checkExist(args[1]),
                        outputPath == null ? null : new File(outputPath));
                println("Output to " + out);
                return;
            }
            if ("smali".equals(cmd)) {
                OdexUtil.smaliRaw(checkExist(args[1]), outputPath, apiLevel);
                return;
            }
            final String inputPath = args[0];
            final String bootPath = args[1];
            final File input = checkExist(inputPath);
            checkExist(bootPath);
            final int type = getInputType(input);
            if (type == TYPE_ODEX) {
                OdexUtil.odex2dex(inputPath, bootPath, outputPath, apiLevel);
            } else if (type == TYPE_OAT) {
                OatUtil.oat2dex(inputPath, bootPath, outputPath);
            } else {
                exit("Unknown input file type: " + input);
            }
        } else {
            printUsage();
        }
    }

    static String[] shiftArgs(String[] args, int n) {
        if (n >= args.length) {
            return args;
        }
        final String[] shiftArgs = new String[args.length - n];
        System.arraycopy(args, n, shiftArgs, 0, shiftArgs.length);
        return shiftArgs;
    }

    static File checkExist(String path) {
        final File input = new File(path);
        if (!input.exists()) {
            exit("Input file not found: " + input);
        }
        return input;
    }

    static final int TYPE_ODEX = 1;
    static final int TYPE_OAT = 2;

    static int getInputType(File input) {
        if (input.isDirectory()) {
            File[] files = MiscUtil.getFiles(input.getAbsolutePath(), "dex;odex;oat");
            if (files.length > 0) {
                input = files[0];
            }
        }
        if (MiscUtil.isOdex(input) || MiscUtil.isDex(input)) {
            return TYPE_ODEX;
        }
        if (MiscUtil.isElf(input)) {
            return TYPE_OAT;
        }
        return -1;
    }

    static void println(String s) {
        System.out.println(s);
    }

    static void exit(String msg) {
        println(msg);
        System.exit(1);
    }
}
