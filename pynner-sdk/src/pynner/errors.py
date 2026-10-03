class PynnerError(Exception):
    """An SDK or bridge error."""


class BridgeError(PynnerError):
    def __init__(self, code: str, message: str):
        self.code = code
        super().__init__(f"{code}: {message}")


class RegistrationError(PynnerError):
    pass
