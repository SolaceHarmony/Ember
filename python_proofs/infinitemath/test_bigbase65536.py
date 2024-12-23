import math, sys
from bigbase65536 import BigBase65536, MyFraction, MathOps, GeometricNumber, WaveSymbolic, WaveGeometricTransformer
from typing import Tuple

##############################################################################
# 6) Demonstration and Testing
##############################################################################

def test_arithmetic():
    print("Testing arithmetic operations...")
    # Test BigBase65536 addition
    a = BigBase65536.from_int(65535)
    b = BigBase65536.from_int(1)
    c = BigBase65536.add(a, b)
    print(f"<BigBase65536 {a.to_decimal_str()}> + <BigBase65536 {b.to_decimal_str()}> = <BigBase65536 {c.to_decimal_str()}>")
    
    # Test BigBase65536 multiplication
    c_mul = BigBase65536.mul(a, b)
    print(f"<BigBase65536 {a.to_decimal_str()}> * <BigBase65536 {b.to_decimal_str()}> = <BigBase65536 {c_mul.to_decimal_str()}>")
    
    # Test BigBase65536 division
    quotient, remainder = BigBase65536.divmod(a, b)
    print(f"<BigBase65536 {a.to_decimal_str()}> / <BigBase65536 {b.to_decimal_str()}> = <BigBase65536 {quotient.to_decimal_str()}> remainder <BigBase65536 {remainder.to_decimal_str()}>")
    
def test_invalid_input():
    print("\nTesting invalid input...")
    try:
        BigBase65536.from_string("abc123")
    except ValueError as e:
        print("Correctly rejected non-numeric string")
    try:
        BigBase65536.from_string("")
    except ValueError as e:
        print("Correctly rejected empty string")

def test_fraction_operations():
    print("\nTesting fraction operations...")
    a = MyFraction.from_int(3)
    b = MyFraction.from_decimal_str("1/2")
    print(f"a = {a}")
    print(f"b = {b}")
    print(f"a + b = {a + b}")
    print(f"a * b = {a * b}")
    print(f"a / b = {a / b}")

def test_mathops():
    print("\nTesting MathOps...")
    # Test factorial
    n = BigBase65536.from_int(5)
    fact = MathOps.factorial(n)
    print(f"factorial(<BigBase65536 {n.to_decimal_str()}>) = {fact.to_int()}")
    
    # Test power: 2^3
    base = MyFraction.from_int(2)
    exponent = BigBase65536.from_int(3)
    power_result = MathOps.power(base, exponent)
    print(f"2^<BigBase65536 {exponent.to_decimal_str()}> = {power_result.to_int()}")
    
    for test_n in [1,5,10,100,999, 10000, 65535, 65536, 99999, 1234567]:
        big_n = BigBase65536.from_int(test_n)
        py_q, py_r = divmod(test_n, 5)
        my_q, my_r = BigBase65536.divmod(big_n, BigBase65536.from_int(5))
        assert my_q.to_int() == py_q,  f"{test_n}/5 => mismatch quotient"
        assert my_r.to_int() == py_r,  f"{test_n}/5 => mismatch remainder"
    print("All tests passed for dividing by 5!")

    for test_n in [1,5,10,100,999, 10000, 65535, 65536, 99999, 1234567]:
        big_n = BigBase65536.from_int(test_n)
        py_q, py_r = divmod(test_n, 10)
        my_q, my_r = BigBase65536.divmod(big_n, BigBase65536.from_int(10))
        assert my_q.to_int() == py_q,  f"{test_n}/10 => mismatch quotient"
        assert my_r.to_int() == py_r,  f"{test_n}/10 => mismatch remainder"
    print("All tests passed for dividing by 10!")
    
    for test_n in [1,5,10,100,999, 10000, 65535, 65536, 99999, 1234567]:
        big_n = BigBase65536.from_int(test_n)
        py_q, py_r = divmod(test_n, 100)
        my_q, my_r = BigBase65536.divmod(big_n, BigBase65536.from_int(100))
        assert my_q.to_int() == py_q,  f"{test_n}/100 => mismatch quotient"
        assert my_r.to_int() == py_r,  f"{test_n}/100 => mismatch remainder"
    print("All tests passed for dividing by 100!")
    
    for test_n in [1,5,10,100,999, 10000, 65535, 65536, 99999, 1234567]:
        big_n = BigBase65536.from_int(test_n)
        py_q, py_r = divmod(test_n, 1000)
        my_q, my_r = BigBase65536.divmod(big_n, BigBase65536.from_int(1000))
        assert my_q.to_int() == py_q,  f"{test_n}/1000 => mismatch quotient"
        assert my_r.to_int() == py_r,  f"{test_n}/1000 => mismatch remainder"
    print("All tests passed for dividing by 1000!")
    
    for test_n in [1,5,10,100,999, 10000, 65535, 65536, 99999, 1234567]:
        big_n = BigBase65536.from_int(test_n)
        py_q, py_r = divmod(test_n, 10000)
        my_q, my_r = BigBase65536.divmod(big_n, BigBase65536.from_int(10000))
        assert my_q.to_int() == py_q,  f"{test_n}/10000 => mismatch quotient"
        assert my_r.to_int() == py_r,  f"{test_n}/10000 => mismatch remainder"
    print("All tests passed for dividing by 10000!")
    
    for test_n in [1,5,10,100,999, 10000, 65535, 65536, 99999, 1234567]:
        big_n = BigBase65536.from_int(test_n)
        py_q, py_r = divmod(test_n, 65536)
        my_q, my_r = BigBase65536.divmod(big_n, BigBase65536.from_int(65536))
        assert my_q.to_int() == py_q,  f"{test_n}/65536 => mismatch quotient"
        assert my_r.to_int() == py_r,  f"{test_n}/65536 => mismatch remainder"
    print("All tests passed for dividing by 65536!")
    
    for test_n in [1,5,10,100,999, 10000, 65535, 65536, 99999, 1234567]:
        big_n = BigBase65536.from_int(test_n)
        py_q, py_r = divmod(test_n, 65537)
        my_q, my_r = BigBase65536.divmod(big_n, BigBase65536.from_int(65537))
        assert my_q.to_int() == py_q,  f"{test_n}/65537 => mismatch quotient"
        assert my_r.to_int() == py_r,  f"{test_n}/65537 => mismatch remainder"
    print("All tests passed for dividing by 65537!")
    
    for test_n in [1,5,10,100,999, 10000, 65535, 65536, 99999, 1234567]:
        big_n = BigBase65536.from_int(test_n)
        py_q, py_r = divmod(test_n, 65538)
        my_q, my_r = BigBase65536.divmod(big_n, BigBase65536.from_int(65538))
        assert my_q.to_int() == py_q,  f"{test_n}/65538 => mismatch quotient"
        assert my_r.to_int() == py_r,  f"{test_n}/65538 => mismatch remainder"
    print("All tests passed for dividing by 65538!")
    
def test_small_number_multiplication():
    small = MyFraction.from_decimal_str("1/1000")
    squared = MyFraction.mul(small, small)
    print(f"1/1000 * 1/1000 = {squared} (should be 1/1000000)")
    cubed = MyFraction.mul(squared, small)
    print(f"(1/1000)^3 = {cubed} (should be 1/1000000000)")
        
import math


def test_sine_special_values():
    """Test sine function against known exact values."""
    # Get π with good precision (10 terms is enough with Machin's formula)
    pi = MathOps.pi(terms=10)
    print(f"Using π ≈ {pi}")
    
    # Test cases: (angle fraction, expected result fraction, description)
    test_cases = [
        (MyFraction.from_int(0), MyFraction.from_int(0), "sin(0) = 0"),
        
        # sin(π/6) = 1/2
        (MyFraction.div(pi, MyFraction.from_int(6)), 
            MyFraction.div(MyFraction.from_int(1), MyFraction.from_int(2)),
            "sin(π/6) = 1/2"),
            
        # sin(π/2) = 1
        (MyFraction.div(pi, MyFraction.from_int(2)), 
            MyFraction.from_int(1),
            "sin(π/2) = 1"),
    ]
    
    print("\nTesting special values:")
    print("=" * 50)
    
    for angle, expected, description in test_cases:
        # Calculate with our implementation
        result = MathOps.sin(angle, max_terms=20)
        
        # Convert to decimal for display
        result_float = result.to_float()
        expected_float = expected.to_float()
        
        # Calculate error
        error = abs(result_float - expected_float)
        
        print(f"\nTest: {description}")
        print(f"Angle: {angle}")
        print(f"Expected: {expected} ≈ {expected_float}")
        print(f"Got: {result} ≈ {result_float}")
        print(f"Absolute error: {error}")
  
  
def test_sin_near_zero():
    # x = 0
    x_bb = BigBase65536.from_int(0)
    x_frac = MyFraction(x_bb, BigBase65536.from_int(1))
    sin_result = MathOps.sin(x_frac, max_terms=10)
    
    # Compare using BigBase65536 numbers directly
    zero = BigBase65536.from_int(0)
    assert BigBase65536._compare_abs(sin_result.numerator, zero) == 0, \
           f"sin(0) numerator should be 0, got {sin_result.numerator.to_decimal_str()}"

    # Test x = 1/1000
    x_frac_small = MyFraction.from_decimal_str("1/1000")
    sin_small = MathOps.sin(x_frac_small, max_terms=10)
    
    # Compare against pre-computed exact fraction for sin(0.001)
    expected_num = BigBase65536.from_string("7155592949097036013949761039277318899020737281149119994528000020117647")
    expected_den = BigBase65536.from_string("7155594141696000000000000000000000000000000000000000000000000000000000000")
    expected = MyFraction(expected_num, expected_den)
    
    # Compare numerators and denominators directly
    assert BigBase65536._compare_abs(sin_small.numerator, expected.numerator) == 0 \
        and BigBase65536._compare_abs(sin_small.denominator, expected.denominator) == 0, \
        f"sin(0.001) mismatch\nGot: {sin_small.numerator.to_decimal_str()}/{sin_small.denominator.to_decimal_str()}\nExpected: {expected_num.to_decimal_str()}/{expected_den.to_decimal_str()}"

def test_geometric():
    print("\nTesting geometric transformations...")
    n = MyFraction.from_int(5)
    triangular = GeometricNumber(n, "triangular").to_geometric_fraction()  # 5*6/2 = 15
    square = GeometricNumber(n, "square").to_geometric_fraction()          # 5*5 = 25
    tetrahedral = GeometricNumber(n, "tetrahedral").to_geometric_fraction()# 5*6*7/6 = 35
    print(f"Triangular(5) = {triangular} (Expected: 15/1)")
    print(f"Square(5) = {square} (Expected: 25/1)")
    print(f"Tetrahedral(5) = {tetrahedral} (Expected: 35/1)")

def test_wave():
    print("\nTesting wave evaluations...")
    # Create large fractions
    large_num = MyFraction.from_int(12345678901234567890)
    large_denom = MyFraction.from_int(98765432109876543210)
    large_frac = MyFraction.div(large_num, large_denom)  # 12345678901234567890 / 98765432109876543210
    
    # Create geometric representation as a triangular number
    geometric = GeometricNumber(large_frac, "triangular")
    geometric_frac = geometric.to_geometric_fraction()
    
    # Create wave representation with geometric amplitude
    wave = WaveSymbolic(
        amplitude=geometric_frac,
        freq=MyFraction.from_int(1),    # 1 Hz
        phase=MyFraction.from_int(0)    # 0 phase
    )
    
    # Combine them with a transformer
    transformer = WaveGeometricTransformer(wave, geometric)
    
    # Evaluate at t=1.23 seconds and get BigBase65536 numerator and denominator
    t = 1.23
    numerator, denominator = transformer.evaluate_final_bigbase(t, terms=10)
    print(f"Final numeric output at t={t}s => {numerator.to_decimal_str()}/{denominator.to_decimal_str()}")
    
    # For a more comprehensive test, evaluate across multiple time points
    time_points = [0.0, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0]
    numeric_outputs = [transformer.evaluate_final(t, terms=10) for t in time_points]
    print(f"Numeric wave samples => {[f'{val.numerator.to_decimal_str()}/{val.denominator.to_decimal_str()}' for val in numeric_outputs]}")

if __name__ == "__main__":
    print("=== Comprehensive Test Suite ===")
    test_sine_special_values()
    test_small_number_multiplication()
    test_arithmetic()
    test_invalid_input()
    test_fraction_operations()
    test_mathops()
    test_sin_near_zero()
    test_geometric()
    test_wave()