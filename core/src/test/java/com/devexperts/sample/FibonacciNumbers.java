package com.devexperts.sample;

/**
 * @author Dmitry Paraschenko
 */
public class FibonacciNumbers {
	private static int fib(int n) {
		if (n < 2)
			return 1;
		return fib(n - 1) + fib(n - 2);
	}

	public static void main(String[] args) {
		long time = System.currentTimeMillis();
		int n = Integer.parseInt(args[0]);
		System.out.printf("fib(%d)=%d\n", n, fib(n));
		System.out.printf("elapsed time=%d\n", System.currentTimeMillis() - time);
	}
}
