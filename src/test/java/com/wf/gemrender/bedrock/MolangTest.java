package com.wf.gemrender.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MolangTest {

	private static double eval(String source, float timeSeconds) {
		return Molang.parse(source)
				.evaluate(timeSeconds);
	}

	@Test
	@DisplayName("a number is a constant, and a constant knows it is one")
	void numbersAreConstants() {
		assertThat(eval("3.5", 0.0f)).isEqualTo(3.5);
		assertThat(Molang.parse("3.5")
				.isConstant()).isTrue();
		assertThat(Molang.parse("-2")
				.evaluate(0.0f)).isEqualTo(-2.0);
	}

	@Test
	@DisplayName("arithmetic respects precedence and parentheses")
	void arithmeticHasPrecedence() {
		assertThat(eval("1 + 2 * 3", 0.0f)).isEqualTo(7.0);
		assertThat(eval("(1 + 2) * 3", 0.0f)).isEqualTo(9.0);
		assertThat(eval("10 - 2 - 3", 0.0f)).as("subtraction is left-associative")
				.isEqualTo(5.0);
		assertThat(eval("8 / 4 / 2", 0.0f)).isEqualTo(1.0);
		assertThat(eval("7 % 4", 0.0f)).isEqualTo(3.0);
	}

	@Test
	@DisplayName("dividing by zero yields the numerator rather than an infinity")
	void divisionByZeroIsGuarded() {
		assertThat(eval("5 / 0", 0.0f)).isEqualTo(5.0);
		assertThat(eval("5 % 0", 0.0f)).isEqualTo(5.0);
	}

	@Test
	@DisplayName("query.anim_time is the driver's own time, so an expression stays a function of it")
	void animTimeIsTheClock() {
		assertThat(eval("query.anim_time", 0.75f)).isCloseTo(0.75, within(1e-6));
		assertThat(eval("q.anim_time * 2", 1.5f)).isCloseTo(3.0, within(1e-6));
		assertThat(Molang.parse("query.anim_time")
				.isConstant()).isFalse();
	}

	@Test
	@DisplayName("sin and cos take degrees, which is Molang's convention and not Java's")
	void trigonometryIsInDegrees() {
		assertThat(eval("math.sin(90)", 0.0f)).isCloseTo(1.0, within(1e-6));
		assertThat(eval("math.cos(180)", 0.0f)).isCloseTo(-1.0, within(1e-6));
		assertThat(eval("math.asin(1)", 0.0f)).isCloseTo(90.0, within(1e-4));
		assertThat(eval("math.atan2(1, 0)", 0.0f)).isCloseTo(90.0, within(1e-4));
	}

	@Test
	@DisplayName("the supported functions compute what their names say")
	void functionsAreCorrect() {
		assertThat(eval("math.abs(-3)", 0.0f)).isEqualTo(3.0);
		assertThat(eval("math.floor(2.7)", 0.0f)).isEqualTo(2.0);
		assertThat(eval("math.ceil(2.1)", 0.0f)).isEqualTo(3.0);
		assertThat(eval("math.round(2.5)", 0.0f)).isEqualTo(3.0);
		assertThat(eval("math.trunc(-2.7)", 0.0f)).isEqualTo(-2.0);
		assertThat(eval("math.sqrt(9)", 0.0f)).isEqualTo(3.0);
		assertThat(eval("math.pow(2, 10)", 0.0f)).isEqualTo(1024.0);
		assertThat(eval("math.min(3, 5)", 0.0f)).isEqualTo(3.0);
		assertThat(eval("math.max(3, 5)", 0.0f)).isEqualTo(5.0);
		assertThat(eval("math.clamp(9, 0, 4)", 0.0f)).isEqualTo(4.0);
		assertThat(eval("math.lerp(10, 20, 0.25)", 0.0f)).isCloseTo(12.5, within(1e-6));
		assertThat(eval("math.pi", 0.0f)).isCloseTo(Math.PI, within(1e-9));
		assertThat(eval("math.to_rad(180)", 0.0f)).isCloseTo(Math.PI, within(1e-9));
	}

	@Test
	@DisplayName("Blockbench's return statement and trailing semicolon are accepted")
	void returnFormIsAccepted() {
		assertThat(eval("return 1 + 1;", 0.0f)).isEqualTo(2.0);
		assertThat(eval("  MATH.SIN( 90 )  ", 0.0f)).as("case and whitespace are normalised")
				.isCloseTo(1.0, within(1e-6));
	}

	@Test
	@DisplayName("anything outside the subset is refused, loudly, rather than evaluated as zero")
	void unsupportedExpressionsThrow() {
		assertThatThrownBy(() -> Molang.parse("query.life_time"))
				.isInstanceOf(Molang.MolangException.class)
				.hasMessageContaining("query.life_time");
		assertThatThrownBy(() -> Molang.parse("math.random(0, 1)"))
				.as("not pure, so it would hand one instance's value to every instance sharing a bucket")
				.isInstanceOf(Molang.MolangException.class);
		assertThatThrownBy(() -> Molang.parse("variable.speed"))
				.isInstanceOf(Molang.MolangException.class);
		assertThatThrownBy(() -> Molang.parse("v.x = 1"))
				.isInstanceOf(Molang.MolangException.class);
		assertThatThrownBy(() -> Molang.parse("query.anim_time > 1 ? 2 : 3"))
				.isInstanceOf(Molang.MolangException.class);
		assertThatThrownBy(() -> Molang.parse("math.sin(90"))
				.isInstanceOf(Molang.MolangException.class)
				.hasMessageContaining("')'");
		assertThatThrownBy(() -> Molang.parse("math.pow(2)"))
				.isInstanceOf(Molang.MolangException.class)
				.hasMessageContaining("arguments");
		assertThatThrownBy(() -> Molang.parse(""))
				.isInstanceOf(Molang.MolangException.class);
	}

	@Test
	@DisplayName("two expressions describing the same thing compare equal")
	void expressionsHaveValueEquality() {
		assertThat(Molang.parse("math.sin(q.anim_time * 360) * 3"))
				.isEqualTo(Molang.parse("math.sin(query.anim_time*360)*3"))
				.hasSameHashCodeAs(Molang.parse("math.sin(q.anim_time * 360) * 3"));

		assertThat(Molang.parse("1 + 2")).isNotEqualTo(Molang.parse("2 + 1"));
	}

	@Test
	@DisplayName("an expression with no time in it folds to a literal")
	void constantFolding() {
		assertThat(Molang.parse("math.sqrt(4) * 3")
				.isConstant()).isTrue();
		assertThat(Molang.parse("math.sqrt(4) * q.anim_time")
				.isConstant()).isFalse();
	}
}
