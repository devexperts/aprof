/*
 *  Aprof - Java Memory Allocation Profiler
 *  Copyright (C) 2002-2012  Devexperts LLC
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.devexperts.aprof.transformer;

import com.devexperts.aprof.AProfRegistry;
import com.devexperts.aprof.LocationStack;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * @author Dmitry Paraschenko
 */
class InvokedMethodTracker extends AdviceAdapter {
    private final GeneratorAdapter mv;
    private final Context context;

    InvokedMethodTracker(GeneratorAdapter mv, Context context) {
        super(mv, context.getAccess(), context.getMethodName(), context.getDescription());
        this.mv = mv;
        this.context = context;
    }

    @Override
    protected void onMethodEnter() {
        context.declareLocationStack(mv);
        if (context.isMethodTracked()) {
            visitMarkInvokedMethod();
        }
    }

    @Override
    protected void onMethodExit(int opcode) {
        if (context.isMethodTracked()) {
            visitUnmarkInvokedMethod();
        }
        if (context.isObjectInit()) {
            visitObjectInit();
        }
    }

    /**
     * @see com.devexperts.aprof.AProfOps#markInvokedMethod(int)
     */
    private void visitMarkInvokedMethod() {
        context.pushLocationStack(mv);
        mv.push(AProfRegistry.registerLocation(context.getLocation()));
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AProfTransformer.LOCATION_STACK, "addInvokedMethod", AProfTransformer.INT_VOID);
    }

    /**
     * @see com.devexperts.aprof.AProfOps#unmarkInvokedMethod()
     */
    private void visitUnmarkInvokedMethod() {
        context.pushLocationStack(mv);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AProfTransformer.LOCATION_STACK, "removeInvokedMethod", AProfTransformer.NOARG_VOID);
    }

    /**
     * @see com.devexperts.aprof.AProfOps#objectInit(Object)
     * @see com.devexperts.aprof.AProfOps#objectInitSize(Object)
     */
    private void visitObjectInit() {
        if (context.getConfig().isUnknown()) {
            mv.loadThis();
            if (context.getConfig().isSize()) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.APROF_OPS, "objectInitSize", AProfTransformer.OBJECT_VOID);
            } else {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, AProfTransformer.APROF_OPS, "objectInit", AProfTransformer.OBJECT_VOID);
            }
        }
    }
}
