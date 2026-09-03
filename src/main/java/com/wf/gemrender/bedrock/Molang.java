package com.wf.gemrender.bedrock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public sealed interface Molang {
	final class MolangException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		MolangException(String message) {
			super(message);
		}
	}

	double evaluate(float timeSeconds);

	default boolean isConstant() {
		return false;
	}

	record Const(double value) implements Molang {
		@Override
		public double evaluate(float timeSeconds) {
			return value;
		}

		@Override
		public boolean isConstant() {
			return true;
		}
	}

	record AnimTime() implements Molang {
		@Override
		public double evaluate(float timeSeconds) {
			return timeSeconds;
		}
	}

	record Negate(Molang value) implements Molang {
		@Override
		public double evaluate(float timeSeconds) {
			return -value.evaluate(timeSeconds);
		}

		@Override
		public boolean isConstant() {
			return value.isConstant();
		}
	}

	record Binary(char operator, Molang left, Molang right) implements Molang {
		@Override
		public double evaluate(float timeSeconds) {
			double a = left.evaluate(timeSeconds);
			double b = right.evaluate(timeSeconds);
			return switch (operator) {
				case '+' -> a + b;
				case '-' -> a - b;
				case '*' -> a * b;

				case '/' -> b == 0.0 ? a : a / b;
				default -> b == 0.0 ? a : a % b;
			};
		}

		@Override
		public boolean isConstant() {
			return left.isConstant() && right.isConstant();
		}
	}

	record Call(Fn function, List<Molang> arguments) implements Molang {
		public Call {
			arguments = List.copyOf(arguments);
		}

		@Override
		public double evaluate(float timeSeconds) {
			double a = arguments.get(0)
					.evaluate(timeSeconds);
			double b = arguments.size() > 1 ? arguments.get(1)
					.evaluate(timeSeconds) : 0.0;
			double c = arguments.size() > 2 ? arguments.get(2)
					.evaluate(timeSeconds) : 0.0;
			return function.apply(a, b, c);
		}

		@Override
		public boolean isConstant() {
			for (Molang argument : arguments) {
				if (!argument.isConstant()) {
					return false;
				}
			}
			return true;
		}
	}

	enum Fn {
		ABS("abs", 1), ACOS("acos", 1), ASIN("asin", 1), ATAN("atan", 1), ATAN2("atan2", 2),
		CEIL("ceil", 1), CLAMP("clamp", 3), COS("cos", 1), EXP("exp", 1), FLOOR("floor", 1),
		LERP("lerp", 3), LN("ln", 1), MAX("max", 2), MIN("min", 2), MOD("mod", 2), POW("pow", 2),
		ROUND("round", 1), SIN("sin", 1), SQRT("sqrt", 1), TO_DEG("to_deg", 1), TO_RAD("to_rad", 1),
		TRUNC("trunc", 1);

		private final String key;
		private final int arity;

		Fn(String key, int arity) {
			this.key = key;
			this.arity = arity;
		}

		public int arity() {
			return arity;
		}

		double apply(double a, double b, double c) {
			return switch (this) {
				case ABS -> Math.abs(a);
				case ACOS -> Math.toDegrees(Math.acos(a));
				case ASIN -> Math.toDegrees(Math.asin(a));
				case ATAN -> Math.toDegrees(Math.atan(a));
				case ATAN2 -> Math.toDegrees(Math.atan2(a, b));
				case CEIL -> Math.ceil(a);
				case CLAMP -> Math.min(Math.max(a, b), c);
				case COS -> Math.cos(Math.toRadians(a));
				case EXP -> Math.exp(a);
				case FLOOR -> Math.floor(a);
				case LERP -> a + (b - a) * c;
				case LN -> Math.log(a);
				case MAX -> Math.max(a, b);
				case MIN -> Math.min(a, b);
				case MOD -> b == 0.0 ? a : a % b;
				case POW -> Math.pow(a, b);
				case ROUND -> Math.round(a);
				case SIN -> Math.sin(Math.toRadians(a));
				case SQRT -> Math.sqrt(a);
				case TO_DEG -> Math.toDegrees(a);
				case TO_RAD -> Math.toRadians(a);
				case TRUNC -> (long) a;
			};
		}

		static Fn of(String name) {
			for (Fn function : values()) {
				if (function.key.equals(name)) {
					return function;
				}
			}
			throw new MolangException("'math." + name + "' is outside the Molang subset GemRender supports");
		}
	}

	static Molang parse(String source) {
		return new Parser(source).run();
	}

	final class Parser {
		private final String original;
		private final String text;
		private int cursor;

		Parser(String source) {
			this.original = source;

			String normalised = source.toLowerCase(Locale.ROOT)
					.replaceAll("\\s+", "");
			if (normalised.startsWith("return")) {
				normalised = normalised.substring("return".length());
			}
			while (normalised.endsWith(";")) {
				normalised = normalised.substring(0, normalised.length() - 1);
			}
			this.text = normalised;
		}

		Molang run() {
			if (text.isEmpty()) {
				throw fail("is empty");
			}
			Molang value = expression();
			if (cursor != text.length()) {
				throw fail("has trailing '" + text.substring(cursor) + "'");
			}
			return value;
		}

		private Molang expression() {
			Molang left = term();
			while (cursor < text.length() && (text.charAt(cursor) == '+' || text.charAt(cursor) == '-')) {
				char operator = text.charAt(cursor++);
				left = new Binary(operator, left, term());
			}
			return left;
		}

		private Molang term() {
			Molang left = unary();
			while (cursor < text.length() && (text.charAt(cursor) == '*' || text.charAt(cursor) == '/'
					|| text.charAt(cursor) == '%')) {
				char operator = text.charAt(cursor++);
				left = new Binary(operator, left, unary());
			}
			return left;
		}

		private Molang unary() {
			if (cursor < text.length() && text.charAt(cursor) == '-') {
				cursor++;
				return new Negate(unary());
			}
			if (cursor < text.length() && text.charAt(cursor) == '+') {
				cursor++;
				return unary();
			}
			return primary();
		}

		private Molang primary() {
			if (cursor >= text.length()) {
				throw fail("ends where a value was expected");
			}

			char c = text.charAt(cursor);
			if (c == '(') {
				cursor++;
				Molang inner = expression();
				expect(')');
				return inner;
			}
			if (c == '.' || (c >= '0' && c <= '9')) {
				return number();
			}
			return identifier();
		}

		private Molang number() {
			int start = cursor;
			while (cursor < text.length()
					&& (Character.isDigit(text.charAt(cursor)) || text.charAt(cursor) == '.')) {
				cursor++;
			}
			try {
				return new Const(Double.parseDouble(text.substring(start, cursor)));
			} catch (NumberFormatException e) {
				throw fail("has '" + text.substring(start, cursor) + "' where a number was expected");
			}
		}

		private Molang identifier() {
			int start = cursor;
			while (cursor < text.length() && (Character.isLetterOrDigit(text.charAt(cursor))
					|| text.charAt(cursor) == '_' || text.charAt(cursor) == '.')) {
				cursor++;
			}
			String name = text.substring(start, cursor);

			if (name.equals("query.anim_time") || name.equals("q.anim_time")) {
				return new AnimTime();
			}
			if (name.equals("math.pi")) {
				return new Const(Math.PI);
			}
			if (!name.startsWith("math.")) {
				throw fail("uses '" + name + "', which is outside the Molang subset GemRender supports");
			}

			Fn function = Fn.of(name.substring("math.".length()));
			expect('(');
			List<Molang> arguments = new ArrayList<>(function.arity());
			arguments.add(expression());
			while (cursor < text.length() && text.charAt(cursor) == ',') {
				cursor++;
				arguments.add(expression());
			}
			expect(')');

			if (arguments.size() != function.arity()) {
				throw fail("calls '" + name + "' with " + arguments.size() + " arguments, not "
						+ function.arity());
			}
			return new Call(function, arguments);
		}

		private void expect(char c) {
			if (cursor >= text.length() || text.charAt(cursor) != c) {
				throw fail("is missing a '" + c + "'");
			}
			cursor++;
		}

		private MolangException fail(String what) {
			return new MolangException("Molang expression \"" + original + "\" " + what);
		}
	}
}
