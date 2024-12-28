from typing import List, Tuple, Optional
import math
import random

class BigBase65536:
    BASE = 65536  # 2^16

    def __init__(self, digits=None, negative=False):
        if digits is None:
            digits = []
        self.digits = digits  # list of 0..65535
        self.negative = negative
        self._trim_leading_zeros()

    def _trim_leading_zeros(self):
        while len(self.digits) > 1 and self.digits[-1] == 0:
            self.digits.pop()
        if len(self.digits) == 1 and self.digits[0] == 0:
            self.negative = False

    @staticmethod
    def divmod_small(a: "BigBase65536", small_val: int) -> Tuple["BigBase65536", int]:
        """
        Chunk-based divmod by small_val (<= 65535). Returns (quotient, remainder).
        quotient is BigBase65536, remainder is a standard int in [0..small_val-1].
        """
        remainder = 0
        out_digits = [0]*len(a.digits)

        for i in reversed(range(len(a.digits))):
            # combine remainder with current digit
            current = (remainder << 16) + a.digits[i]
            qdigit = current // small_val
            remainder = current % small_val
            out_digits[i] = qdigit

        quotient = BigBase65536(out_digits, negative=a.negative)
        quotient._trim_leading_zeros()

        return (quotient, remainder)

    def to_decimal_str(self) -> str:
        """
        Convert this BigBase65536 to a decimal string,
        purely via repeated divmod by 10. No Python int(...).
        """
        # Check if zero:
        if len(self.digits) == 1 and self.digits[0] == 0:
            return "0"

        # Make a copy so we don't mutate self
        tmp_digits = self.digits[:]
        tmp = BigBase65536(tmp_digits, negative=False)
        neg = self.negative

        digits_rev = []
        while not (len(tmp.digits) == 1 and tmp.digits[0] == 0):
            tmp, rem = BigBase65536.divmod_small(tmp, 10)
            digits_rev.append(str(rem))

        decimal_str = "".join(reversed(digits_rev))
        if neg:
            decimal_str = "-" + decimal_str
        return decimal_str

    @staticmethod
    def divmod(a: "BigBase65536", b: "BigBase65536") -> Tuple["BigBase65536", "BigBase65536"]:
        """
        Divide a by b, returning (quotient, remainder) in base-65536,
        without converting to Python int. Implements a 'grade-school' long division
        that works even for two-digit divisors like 65536.
        """
        # 0) Check for zero divisor
        if len(b.digits) == 1 and b.digits[0] == 0:
            raise ZeroDivisionError("divmod() by zero")

        # 1) Handle signs
        a_neg = a.negative
        b_neg = b.negative
        quotient_sign = (a_neg != b_neg)

        # Use absolute-value copies
        a_abs = BigBase65536(a.digits[:], negative=False)
        b_abs = BigBase65536(b.digits[:], negative=False)

        # 2) If |a| < |b| => quotient=0, remainder=a
        cmp_val = BigBase65536._compare_abs(a_abs, b_abs)
        if cmp_val < 0:
            return (
                BigBase65536([0], negative=False),
                BigBase65536(a.digits[:], negative=a_neg),
            )

        # 3) If b_abs has a single digit => do divmod_small
        if len(b_abs.digits) == 1:
            small_val = b_abs.digits[0]
            q, r = BigBase65536.divmod_small(a_abs, small_val)
            q.negative = quotient_sign
            r_big = BigBase65536.from_int(r)
            r_big.negative = a_neg
            return (q, r_big)

        # 4) Long-division in base 65536
        n = len(a_abs.digits)
        m = len(b_abs.digits)

        # We'll store up to n digits in the quotient (worst case).
        # For example, if a=65536^5, we might get 5 or 6 digits in quotient.
        quotient_digits = [0] * n

        remainder = BigBase65536([], negative=False)

        # Go digit-by-digit from the top (index = n-1) down to 0
        for i in range(n - 1, -1, -1):
            # remainder = remainder * BASE + a_abs.digits[i]
            remainder = BigBase65536.mul(
                remainder,
                BigBase65536.from_int(BigBase65536.BASE)
            )
            remainder = BigBase65536.add(
                remainder,
                BigBase65536.from_int(a_abs.digits[i])
            )

            # Binary search 0..65535
            low, high = 0, BigBase65536.BASE - 1
            guess = 0
            while low <= high:
                mid = (low + high) // 2
                prod_mid = BigBase65536.mul(b_abs, BigBase65536.from_int(mid))
                if BigBase65536._compare_abs(prod_mid, remainder) <= 0:
                    guess = mid
                    low = mid + 1
                else:
                    high = mid - 1

            if guess > 0:
                prod_guess = BigBase65536.mul(b_abs, BigBase65536.from_int(guess))
                remainder = BigBase65536._subtract_abs(remainder, prod_guess)

            # Store this guess in quotient_digits at index = i
            # Because we're iterating i downward, the final quotient might be reversed
            # in the sense that 'i=0' is the lowest digit, 'i=1' is next, etc.
            quotient_digits[i] = guess

        # Build final quotient object
        quotient = BigBase65536(quotient_digits, negative=quotient_sign)
        quotient._trim_leading_zeros()

        # Remainder uses the sign of 'a'
        remainder.negative = a_neg
        remainder._trim_leading_zeros()

        return (quotient, remainder)
    
    @classmethod
    def from_string(cls, decimal_str: str) -> "BigBase65536":
        """Create from decimal string."""
        if not decimal_str:
            raise ValueError("Empty string")
            
        negative = decimal_str.startswith('-')
        if negative:
            decimal_str = decimal_str[1:]
            
        if not decimal_str.isdigit():
            raise ValueError("Invalid decimal string")

        # Initialize with zero
        result = cls([0], negative=False)
        
        # Process each digit: result = result * 10 + digit
        for digit in decimal_str:
            # Multiply by 10
            digit_val = ord(digit) - ord('0')
            result_times_10 = []
            carry = 0
            
            # First multiply each digit by 10
            for d in result.digits:
                prod = d * 10 + carry
                result_times_10.append(prod & 0xFFFF)
                carry = prod >> 16
            if carry:
                result_times_10.append(carry)
                
            # Add the new digit
            if digit_val:
                carry = digit_val
                idx = 0
                while carry and idx < len(result_times_10):
                    total = result_times_10[idx] + carry
                    result_times_10[idx] = total & 0xFFFF
                    carry = total >> 16
                    idx += 1
                if carry:
                    result_times_10.append(carry)
                    
            result.digits = result_times_10
            
        result.negative = negative
        result._trim_leading_zeros()
        return result
    
    @classmethod
    def from_int(cls, value: int) -> "BigBase65536":
        if value == 0:
            return cls([0], negative=False)
        negative = (value < 0)
        value = abs(value)
        digits = []
        while value > 0:
            digits.append(value % cls.BASE)
            value //= cls.BASE
        return cls(digits, negative=negative)

    def to_int(self) -> int:
        result = 0
        mul = 1
        for chunk in self.digits:
            result += chunk * mul
            mul *= self.BASE
        return -result if self.negative else result

    def __repr__(self):
        # Let to_decimal_str() handle the sign and preserve large digits
        return f"<BigBase65536 {self.to_decimal_str()}>"

    @staticmethod
    def _compare_abs(a: "BigBase65536", b: "BigBase65536"):
        """
        Compare absolute values of a and b.
        Return +1 if |a| > |b|, -1 if |a| < |b|, 0 if equal.
        """
        if len(a.digits) > len(b.digits):
            return 1
        elif len(a.digits) < len(b.digits):
            return -1
        else:
            for i in reversed(range(len(a.digits))):
                if a.digits[i] > b.digits[i]:
                    return 1
                elif a.digits[i] < b.digits[i]:
                    return -1
            return 0

    @staticmethod
    def _subtract_abs(big, small):
        """
        Subtract |small| from |big|, assuming |big| >= |small|.
        Sign of result = big.negative
        """
        result_digits = []
        carry = 0
        for i in range(len(big.digits)):
            da = big.digits[i]
            db = small.digits[i] if i < len(small.digits) else 0
            s = da - db - carry
            if s < 0:
                s += 65536
                carry = 1
            else:
                carry = 0
            result_digits.append(s & 0xFFFF)

        result = BigBase65536(result_digits, negative=big.negative)
        result._trim_leading_zeros()
        return result

    @staticmethod
    def add(a: "BigBase65536", b: "BigBase65536") -> "BigBase65536":
        if a.negative == b.negative:
            # Same sign => add magnitudes
            result_digits = []
            carry = 0
            max_len = max(len(a.digits), len(b.digits))
            for i in range(max_len):
                da = a.digits[i] if i < len(a.digits) else 0
                db = b.digits[i] if i < len(b.digits) else 0
                s = da + db + carry
                carry = s >> 16
                s &= 0xFFFF
                result_digits.append(s)
            if carry:
                result_digits.append(carry)
            result = BigBase65536(result_digits, negative=a.negative)
            result._trim_leading_zeros()
            return result
        else:
            # Opposite signs => subtract smaller magnitude from larger
            cmp_val = BigBase65536._compare_abs(a, b)
            if cmp_val == 0:
                return BigBase65536([0], negative=False)
            elif cmp_val > 0:
                # |a| > |b| => result sign = a.negative
                return BigBase65536._subtract_abs(a, b)
            else:
                # |b| > |a| => result sign = b.negative
                return BigBase65536._subtract_abs(b, a)

    @staticmethod
    def mul(a: "BigBase65536", b: "BigBase65536") -> "BigBase65536":
        negative = (a.negative != b.negative)
        result_len = len(a.digits) + len(b.digits)
        result_digits = [0]*result_len

        for i, da in enumerate(a.digits):
            carry = 0
            for j, db in enumerate(b.digits):
                mul_val = da * db + result_digits[i+j] + carry
                result_digits[i+j] = mul_val & 0xFFFF
                carry = mul_val >> 16
            if carry:
                result_digits[i + len(b.digits)] += carry

        result = BigBase65536(result_digits, negative=negative)
        result._trim_leading_zeros()
        return result

    @staticmethod
    def gcd(a: "BigBase65536", b: "BigBase65536") -> "BigBase65536":
        """
        Compute GCD of two BigBase65536 numbers using the Euclidean algorithm.
        """
        a_abs = BigBase65536(a.digits[:], negative=False)
        b_abs = BigBase65536(b.digits[:], negative=False)
        while not (len(b_abs.digits) == 1 and b_abs.digits[0] == 0):
            _, rem = BigBase65536.divmod(a_abs, b_abs)
            a_abs, b_abs = b_abs, rem
        return a_abs

class MyFraction:
    """
    Fraction class that uses BigBase65536 for numerator/denominator,
    with fully base-65536 gcd (no Python int conversions).
    """

    def __init__(self, numerator: "BigBase65536", denominator: "BigBase65536"):
        # If denominator == 0 => error
        if len(denominator.digits) == 1 and denominator.digits[0] == 0:
            raise ZeroDivisionError("MyFraction denominator cannot be zero.")

        self.numerator = numerator   # BigBase65536
        self.denominator = denominator  # BigBase65536
        self._normalize()

    def _normalize(self):
        """
        1) If denominator is negative, flip signs so denominator is positive.
        2) gcd-reduce using BigBase65536.gcd(...) in pure base-65536.
        """
        # 1) Ensure denominator >= 0 by checking .negative
        if self.denominator.negative:
            self.denominator.negative = False
            self.numerator.negative = not self.numerator.negative

        # 2) gcd => pure base-65536
        gcd_big = BigBase65536.gcd(self.numerator, self.denominator)

        # If gcd_big != 1 => reduce
        # gcd_big == 1 in base-65536 means "len=1, digits[0]=1, negative=False"
        if not (len(gcd_big.digits) == 1 and gcd_big.digits[0] == 1 and gcd_big.negative == False):
            # Divide numerator by gcd_big
            self.numerator, _ = BigBase65536.divmod(self.numerator, gcd_big)
            # Divide denominator by gcd_big
            self.denominator, _ = BigBase65536.divmod(self.denominator, gcd_big)

    @classmethod
    def from_decimal_str(cls, dec_str: str) -> "MyFraction":
        """
        Parse a decimal string into MyFraction. Supports '123/456' or a simple int '123'.
        """
        s = dec_str.strip()
        if '/' in s:
            num_str, den_str = s.split('/', 1)
            num_bb = BigBase65536.from_string(num_str)
            den_bb = BigBase65536.from_string(den_str)
            return cls(num_bb, den_bb)
        else:
            num_bb = BigBase65536.from_string(s)
            den_bb = BigBase65536.from_string("1")
            return cls(num_bb, den_bb)

    @classmethod
    def from_int(cls, val: int) -> "MyFraction":
        """
        Create MyFraction from a Python int (small-range usage).
        """
        return cls(BigBase65536.from_int(val), BigBase65536.from_int(1))

    @staticmethod
    def add(a: "MyFraction", b: "MyFraction") -> "MyFraction":
        # (a/b) + (c/d) => (ad + bc)/bd
        ad = BigBase65536.mul(a.numerator, b.denominator)
        bc = BigBase65536.mul(b.numerator, a.denominator)
        new_num = BigBase65536.add(ad, bc)
        new_den = BigBase65536.mul(a.denominator, b.denominator)
        return MyFraction(new_num, new_den)

    @staticmethod
    def sub(a: "MyFraction", b: "MyFraction") -> "MyFraction":
        # (a/b) - (c/d) => (ad - bc)/bd
        ad = BigBase65536.mul(a.numerator, b.denominator)
        bc = BigBase65536.mul(b.numerator, a.denominator)

        neg_bc = BigBase65536(bc.digits[:], negative=not bc.negative)
        new_num = BigBase65536.add(ad, neg_bc)

        new_den = BigBase65536.mul(a.denominator, b.denominator)
        return MyFraction(new_num, new_den)

    @staticmethod
    def mul(a: "MyFraction", b: "MyFraction") -> "MyFraction":
        # (a/b)*(c/d) => (ac)/(bd)
        new_num = BigBase65536.mul(a.numerator, b.numerator)
        new_den = BigBase65536.mul(a.denominator, b.denominator)
        return MyFraction(new_num, new_den)

    @staticmethod
    def div(a: "MyFraction", b: "MyFraction") -> "MyFraction":
        # (a/b) / (c/d) => (ad)/(bc)
        # check if b.numerator == 0 => zero division
        if len(b.numerator.digits) == 1 and b.numerator.digits[0] == 0:
            raise ZeroDivisionError("Division by zero in MyFraction.")

        new_num = BigBase65536.mul(a.numerator, b.denominator)
        new_den = BigBase65536.mul(a.denominator, b.numerator)
        return MyFraction(new_num, new_den)

    def __add__(self, other: "MyFraction") -> "MyFraction":
        return MyFraction.add(self, other)

    def __sub__(self, other: "MyFraction") -> "MyFraction":
        return MyFraction.sub(self, other)

    def __mul__(self, other: "MyFraction") -> "MyFraction":
        return MyFraction.mul(self, other)

    def __truediv__(self, other: "MyFraction") -> "MyFraction":
        return MyFraction.div(self, other)

    def __eq__(self, other: "MyFraction") -> bool:
        """
        Compare fractions purely in base-65536 string form (for numerator & denominator).
        """
        # 1) Compare sign bits
        if self.numerator.negative != other.numerator.negative:
            return False
        if self.denominator.negative != other.denominator.negative:
            return False

        # 2) Compare absolute values of numerator & denominator via decimal string
        num_str_self = self.numerator.to_decimal_str()
        num_str_other = other.numerator.to_decimal_str()
        den_str_self = self.denominator.to_decimal_str()
        den_str_other = other.denominator.to_decimal_str()

        return (num_str_self == num_str_other) and (den_str_self == den_str_other)

    def __str__(self):
        """
        If denominator=1 => print just numerator, else print "num/den".
        Uses base-65536 .to_decimal_str() to keep infinite precision.
        """
        # check if denominator == 1
        one = BigBase65536.from_int(1)
        den_is_one = (BigBase65536._compare_abs(self.denominator, one) == 0
                      and not self.denominator.negative)

        num_str = self.numerator.to_decimal_str()
        sign_str = "-" if self.numerator.negative else ""

        if den_is_one:
            return f"{sign_str}{num_str.lstrip('-')}"
        else:
            den_str = self.denominator.to_decimal_str()
            return f"{sign_str}{num_str.lstrip('-')}/{den_str}"

    def to_int(self) -> int:
        """
        Convert fraction to Python int (floor if denominator != 1).
        1) Convert numerator & denominator to decimal string.
        2) Parse Python int => arbitrary precision.
        3) If denominator != 1 => floor division.
        """
        # Check if denominator=0 => infinite, but in MyFraction we forbid that.
        # So we skip that.

        num_str = self.numerator.to_decimal_str()
        den_str = self.denominator.to_decimal_str()

        sign = -1 if self.numerator.negative else 1
        if num_str.startswith('-'):
            num_str = num_str[1:]  # remove leading '-'

        numerator_int = int(num_str)   # Python can handle large strings
        denominator_int = int(den_str) # same

        if denominator_int == 1:
            return sign * numerator_int
        else:
            return sign * (numerator_int // denominator_int)

    def to_float(self) -> float:
        """
        Convert fraction to float *approximation*:
        1) num->decimal_str => parse as float
        2) den->decimal_str => parse as float
        3) sign from numerator. Then do float division => ~15 digits precision.
        """
        # If denominator=0 => inf, but we disallow that in MyFraction.
        num_str = self.numerator.to_decimal_str()
        den_str = self.denominator.to_decimal_str()

        sign = -1.0 if self.numerator.negative else 1.0
        if num_str.startswith('-'):
            num_str = num_str[1:]

        num_float = float(num_str)
        den_float = float(den_str)

        if den_float == 0.0:
            return float('-inf') if sign < 0 else float('inf')

        return sign * (num_float / den_float)

class SqrtD:
    """
    Represents a + b*sqrt(D), 
    where a, b, and D are all MyFraction.
    """
    def __init__(self, a=None, b=None, D=None):
        if a is None: 
            a = MyFraction.from_int(0)
        if b is None: 
            b = MyFraction.from_int(0)
        if D is None:
            D = MyFraction.from_int(0)
        
        self.a = a  # MyFraction
        self.b = b  # MyFraction
        self.D = D  # MyFraction

    @staticmethod
    def add(x: "SqrtD", y: "SqrtD") -> "SqrtD":
        # Must have same D
        if not (x.D == y.D):
            raise ValueError("Cannot add sqrt(D1) with sqrt(D2) for different D.")
        new_a = MyFraction.add(x.a, y.a)
        new_b = MyFraction.add(x.b, y.b)
        return SqrtD(new_a, new_b, x.D)

    @staticmethod
    def sub(x: "SqrtD", y: "SqrtD") -> "SqrtD":
        if not (x.D == y.D):
            raise ValueError("Cannot subtract sqrt(D1) from sqrt(D2) for different D.")
        new_a = MyFraction.sub(x.a, y.a)
        new_b = MyFraction.sub(x.b, y.b)
        return SqrtD(new_a, new_b, x.D)

    @staticmethod
    def mul(x: "SqrtD", y: "SqrtD") -> "SqrtD":
        if not (x.D == y.D):
            raise ValueError("Cannot multiply sqrt(D1) by sqrt(D2) for different D.")
        # (a + b sqrt(D)) * (c + d sqrt(D)) = (ac + bd*D) + (ad + bc)*sqrt(D)
        
        # ac = x.a * y.a
        ac = MyFraction.mul(x.a, y.a)
        
        # bd = x.b * y.b
        bd = MyFraction.mul(x.b, y.b)
        
        # bd*D
        bdD = MyFraction.mul(bd, x.D)
        
        # new_a = ac + bdD
        new_a = MyFraction.add(ac, bdD)
        
        # ad = x.a * y.b
        ad = MyFraction.mul(x.a, y.b)
        
        # bc = x.b * y.a
        bc = MyFraction.mul(x.b, y.a)
        
        # new_b = ad + bc
        new_b = MyFraction.add(ad, bc)
        
        return SqrtD(new_a, new_b, x.D)

    @staticmethod
    def div(x: "SqrtD", y: "SqrtD") -> "SqrtD":
        if not (x.D == y.D):
            raise ValueError("Cannot divide sqrt(D1) by sqrt(D2) for different D.")
        # Rationalize: 
        # (a + b sqrt(D)) / (c + d sqrt(D)) 
        # = [(a + b sqrt(D)) * (c - d sqrt(D))] / [(c + d sqrt(D)) * (c - d sqrt(D))]
        
        # First compute c^2
        c_squared = MyFraction.mul(y.a, y.a)
        
        # Then d^2*D
        d_squared = MyFraction.mul(y.b, y.b)
        d_squared_D = MyFraction.mul(d_squared, x.D)
        
        # Denominator = c^2 - d^2*D
        denom = MyFraction.sub(c_squared, d_squared_D)
        
        if denom == MyFraction.from_int(0):
            raise ZeroDivisionError("Division by zero in SqrtD.")
            
        # Numerator = (a + b sqrt(D))*(c - d sqrt(D))
        neg_d = MyFraction.mul(MyFraction.from_int(-1), y.b)
        c_negd = SqrtD(y.a, neg_d, x.D)
        numerator = SqrtD.mul(x, c_negd)
        
        # Final division
        new_a = MyFraction.div(numerator.a, denom)
        new_b = MyFraction.div(numerator.b, denom)
        
        return SqrtD(new_a, new_b, x.D)

    def to_float(self) -> float:
        """
        Approximate: float(a) + float(b)*sqrt(D)
        Only used for final display/verification.
        """
        return self.a.to_float() + self.b.to_float()*math.sqrt(self.D.to_float())

    def __str__(self):
        return f"({self.a} + {self.b}*sqrt({self.D}))"

    def __repr__(self):
        return self.__str__()

    @staticmethod
    def from_ints(a: int, b: int, D: int) -> "SqrtD":
        """Convenience method for creating SqrtD from integers during testing."""
        return SqrtD(
            MyFraction.from_int(a),
            MyFraction.from_int(b),
            MyFraction.from_int(D)
        )

class MathOps:
    """Mathematical operations using pure BigBase65536/MyFraction arithmetic."""
    
    factorial_cache = {}
    
    @staticmethod
    def gcd(a: "BigBase65536", b: "BigBase65536") -> "BigBase65536":
        """
        Compute GCD of two BigBase65536 numbers using the Euclidean algorithm.
        """
        a_abs = BigBase65536(a.digits[:], negative=False)
        b_abs = BigBase65536(b.digits[:], negative=False)
        while not (len(b_abs.digits) == 1 and b_abs.digits[0] == 0):
            _, rem = BigBase65536.divmod(a_abs, b_abs)
            a_abs, b_abs = b_abs, rem
        return a_abs

    @staticmethod
    def factorial(n: "BigBase65536") -> "MyFraction":
        """Compute factorial using caching to improve performance."""
        key = n.to_int()
        if key in MathOps.factorial_cache:
            return MathOps.factorial_cache[key]
        
        zero = BigBase65536.from_string("0")
        one = BigBase65536.from_string("1")
        
        if BigBase65536._compare_abs(n, zero) == 0 or \
           BigBase65536._compare_abs(n, one) == 0:
            MathOps.factorial_cache[key] = MyFraction(one, one)
            return MathOps.factorial_cache[key]
                         
        result = MyFraction(one, one)
        i = BigBase65536.from_string("2")
        
        while BigBase65536._compare_abs(i, n) <= 0:
            term = MyFraction(i, one)
            result = MyFraction.mul(result, term)
            result._normalize()  # Reduce after each multiplication
            i = BigBase65536.add(i, one)
            
        MathOps.factorial_cache[key] = result
        return result

    @staticmethod
    def power(x: "MyFraction", n: "BigBase65536") -> "MyFraction":
        """Compute x^n with aggressive fraction reduction."""
        print(f"[POWER] Base: {x.numerator}/{x.denominator}")
        print(f"        Exp:  {n.numerator}/{n.denominator}")
        
        # If base is zero and exponent is positive => short-circuit to zero
        if len(x.numerator.digits) == 1 and x.numerator.digits[0] == 0 and \
        BigBase65536._compare_abs(n, BigBase65536.from_int(0)) > 0:
            return MyFraction.from_int(0)
        # If exponent is 0 => return 1

        if BigBase65536._compare_abs(n, BigBase65536.from_int(0)) == 0:
            print("  Exponent is 0, returning 1")
            return MyFraction.from_int(1)

        # Exponentiation by squaring with reduction
        result = MyFraction.from_int(1)
        base = x
        two = BigBase65536.from_int(2)
        one = BigBase65536.from_int(1)

        print(f"  Starting repeated squaring:")
        print(f"  Initial base = {base.numerator.to_decimal_str()}/{base.denominator.to_decimal_str()}")
        print(f"  Initial result = {result.numerator.to_decimal_str()}/{result.denominator.to_decimal_str()}")

        while BigBase65536._compare_abs(n, BigBase65536.from_int(0)) > 0:
            quotient, remainder = BigBase65536.divmod(n, two)
            print(f"  n = {n.numerator.to_decimal_str()}/{n.denominator.to_decimal_str()}")
            
            if BigBase65536._compare_abs(remainder, one) == 0:
                result = MyFraction.mul(result, base)
                result._normalize()  # Reduce after multiplication
                print(f"    Odd exponent: result *= base => {result.numerator.to_decimal_str()}/{result.denominator.to_decimal_str()}")
            
            base = MyFraction.mul(base, base)
            base._normalize()  # Reduce after squaring
            print(f"    Square base: base *= base => {base.numerator.to_decimal_str()}/{base.denominator.to_decimal_str()}")
            n = quotient

        print(f"[POWER DEBUG] Final result = {result.numerator.to_decimal_str()}/{result.denominator.to_decimal_str()}")
        return result


    @staticmethod
    def sin(x: "MyFraction", max_terms: int = 20) -> "MyFraction":
        """Enhanced sine calculation with better convergence control"""
        result = MyFraction.from_int(0)
        prev_term_size = float('inf')
        
        for n in range(max_terms):
            k = 2*n + 1
            k_bb = BigBase65536.from_int(k)
            
            # Compute x^k more efficiently
            if n == 0:
                pow_xk = x
            else:
                # Use x^2 multiplication instead of k individual multiplications
                pow_xk = x
                x_squared = MyFraction.mul(x, x)
                x_squared._normalize()
                
                for i in range((k-1)//2):
                    pow_xk = MyFraction.mul(pow_xk, x_squared)
                    pow_xk._normalize()
                
                if k % 2 == 1:
                    pow_xk = MyFraction.mul(pow_xk, x)
                    pow_xk._normalize()
            
            fact_k = MathOps.factorial(k_bb)
            term = MyFraction.div(pow_xk, fact_k)
            term._normalize()
            
            # Check convergence
            term_size = len(term.numerator.digits) + len(term.denominator.digits)
            if term_size > prev_term_size and term_size < 2:
                break
            prev_term_size = term_size
            
            if n % 2 == 0:
                result = MyFraction.add(result, term)
            else:
                result = MyFraction.sub(result, term)
            result._normalize()
        
        return result

    @staticmethod
    def cos(x: "MyFraction", max_terms: int = 10) -> "MyFraction":
        """Compute cos(x) using Taylor series with aggressive reduction."""
        result = MyFraction.from_int(1)

        for n in range(1, max_terms):
            k = 2 * n
            k_bb = BigBase65536.from_int(k)
            
            # Calculate x^k with reduction at each step
            pow_xk = MyFraction.from_int(1)
            for _ in range(k):
                pow_xk = MyFraction.mul(pow_xk, x)
                pow_xk._normalize()
            
            fact_k = MathOps.factorial(k_bb)
            term = MyFraction.div(pow_xk, fact_k)
            term._normalize()
            
            if n % 2 == 1:
                result = MyFraction.sub(result, term)
            else:
                result = MyFraction.add(result, term)
            result._normalize()
        
        return result


    @staticmethod
    def tan(x: "MyFraction", max_terms: int = 10) -> "MyFraction":
        """
        Compute tan(x) using sin and cos in pure fraction arithmetic.
        tan(x) = sin(x) / cos(x)
        """
        sine = MathOps.sin(x, max_terms=max_terms)
        cosine = MathOps.cos(x, max_terms=max_terms)
        if cosine == MyFraction.from_int(0):
            raise ZeroDivisionError("Division by zero in MathOps.tan")
        return MathOps.div(sine, cosine)

    @staticmethod
    def arctan(x: "MyFraction", terms: int = 10) -> "MyFraction":
        """
        Compute arctan(x) using Taylor series with reduction:
        arctan(x) = x - x³/3 + x⁵/5 - x⁷/7 + ...
        """
        result = MyFraction.from_int(0)
        x_squared = MyFraction.mul(x, x)
        x_squared._normalize()
        power = x

        for n in range(terms):
            k = 2 * n + 1
            term = MyFraction.div(power, MyFraction.from_int(k))
            term._normalize()
            
            if n % 2 == 0:
                result = MyFraction.add(result, term)
            else:
                result = MyFraction.sub(result, term)
            result._normalize()
            
            power = MyFraction.mul(power, x_squared)
            power._normalize()

        return result
    
    @staticmethod
    def pi(terms: int = 10) -> "MyFraction":
        """
        Compute π using Machin's formula with reduction:
        π/4 = 4 arctan(1/5) - arctan(1/239)
        """
        # Calculate arctan(1/5)
        term1 = MathOps.arctan(
            MyFraction.div(MyFraction.from_int(1), MyFraction.from_int(5)), 
            terms=terms
        )
        
        # Calculate arctan(1/239)
        term2 = MathOps.arctan(
            MyFraction.div(MyFraction.from_int(1), MyFraction.from_int(239)),
            terms=terms
        )
        
        # π/4 = 4 arctan(1/5) - arctan(1/239)
        pi_over_4 = MyFraction.sub(
            MyFraction.mul(MyFraction.from_int(4), term1),
            term2
        )
        pi_over_4._normalize()
        
        # Multiply by 4 to get π
        result = MyFraction.mul(MyFraction.from_int(4), pi_over_4)
        result._normalize()
        return result

##############################################################################
# 2) WaveSymbolic with Fraction-based Evaluate
##############################################################################

class WaveSymbolic:
    """
    A wave: amplitude * sin(2*pi * freq * t + phase),
    with amplitude, freq, phase all in MyFraction form.
    """
    def __init__(self, amplitude: "MyFraction", freq: "MyFraction", phase: "MyFraction"):
        self.amp = amplitude
        self.freq = freq
        self.phase = phase

    def __add__(self, other: "WaveSymbolic") -> "WaveSumSymbolic":
        return WaveSumSymbolic([self, other])

    def evaluate_fraction(self, t: "MyFraction", terms: int = 10) -> "MyFraction":
        """
        Evaluate wave in fraction form using MathOps.sin.
        """
        two_pi = MathOps.mul(MathOps.pi(), MyFraction.from_int(2))  # ~6.283185 (355/113 * 2)
        angle = MyFraction.add(MyFraction.mul(MathOps.mul(two_pi, self.freq), t), self.phase)
        sine = MathOps.sin(angle, max_terms=terms)
        return MyFraction.mul(self.amp, sine)

##############################################################################
# 3) GeometricNumber in Pure Fraction Domain
##############################################################################

class GeometricNumber:
    """
    Stores a fraction 'value' and an associated figurate shape
    (triangular, square, tetrahedral).
    Then 'to_geometric_fraction()' applies the figurate formula in fraction form.
    """
    def __init__(self, value: "MyFraction", shape_type: str):
        self.value = value
        self.shape_type = shape_type

    def to_geometric_fraction(self) -> "MyFraction":
        """
        Return the fraction that is the figurate transform of 'value'.
        """
        n = self.value
        one = MyFraction.from_int(1)
        two = MyFraction.from_int(2)
        six = MyFraction.from_int(6)

        if self.shape_type == "triangular":
            # n(n+1)/2
            return MyFraction.div(MyFraction.mul(n, MyFraction.add(n, one)), two)
        elif self.shape_type == "square":
            # n^2
            return MyFraction.mul(n, n)
        elif self.shape_type == "tetrahedral":
            # n(n+1)(n+2)/6
            return MyFraction.div(MyFraction.mul(MyFraction.mul(n, MyFraction.add(n, one)), MyFraction.add(n, MyFraction.from_int(2))), six)
        else:
            # default: just return n
            return n

##############################################################################
# 4) WaveSumSymbolic Class
##############################################################################

class WaveSumSymbolic:
    """
    A container for summing multiple WaveSymbolic objects.
    We store them in a list and do fraction-based summations or expansions as needed.
    """
    def __init__(self, wave_list: List["WaveSymbolic"]):
        self.wave_list = wave_list

    def __add__(self, other: "WaveSymbolic") -> "WaveSumSymbolic":
        if isinstance(other, WaveSumSymbolic):
            return WaveSumSymbolic(self.wave_list + other.wave_list)
        return WaveSumSymbolic(self.wave_list + [other])

    def evaluate_fraction(self, t: "MyFraction", terms: int = 10) -> "MyFraction":
        """
        Summation of fraction-based evaluations for each sub-wave.
        """
        total = MyFraction.from_int(0)
        for w in self.wave_list:
            total = MyFraction.add(total, w.evaluate_fraction(t, terms=terms))
        return total

##############################################################################
# 5) WaveGeometricTransformer
##############################################################################

class WaveGeometricTransformer:
    """
    Combines a WaveSymbolic with a GeometricNumber
    and modifies the wave amplitude by the figurate transform.
    Then we do a final numeric 'collapse' in 'evaluate_final()'.
    """
    def __init__(self, wave: "WaveSymbolic", geom: "GeometricNumber"):
        self.wave = wave
        self.geom = geom

    def transform_amplitude(self) -> "WaveSymbolic":
        """
        Applies geometric transform to wave's amplitude in fraction form.
        """
        geom_frac = self.geom.to_geometric_fraction()
        new_amp = MyFraction.mul(self.wave.amp, geom_frac)
        return WaveSymbolic(new_amp, self.wave.freq, self.wave.phase)

    def evaluate_final(self, t: float, terms: int = 10) -> "MyFraction":
        """
        The single numeric collapse.
        't' is a float, but we'll convert it to fraction 
        for internal evaluation, then keep the final result as a fraction.
        """
        # Convert float -> fraction: e.g., t * 1000000 => MyFraction
        multiplier = 1000000
        t_int = int(t * multiplier)
        t_frac = MyFraction.from_int(t_int) / MyFraction.from_int(multiplier)

        # Apply geometric transformation
        transformed_wave = self.transform_amplitude()

        # Evaluate wave in fraction domain
        fraction_result = transformed_wave.evaluate_fraction(t_frac, terms=terms)

        # Return fraction_result instead of converting to float
        return fraction_result

    def evaluate_final_bigbase(self, t: float, terms: int = 10) -> Tuple["BigBase65536", "BigBase65536"]:
        """
        Evaluate the final result and return as (numerator, denominator) using BigBase65536.
        """
        fraction_result = self.evaluate_final(t, terms=terms)
        return (fraction_result.numerator, fraction_result.denominator)

