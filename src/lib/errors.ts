export class AppError extends Error {
  constructor(
    message: string,
    public statusCode: number = 500,
  ) {
    super(message);
    this.name = this.constructor.name;
  }
}

export class NotFoundError extends AppError {
  constructor(message = "Not Found") {
    super(message, 404);
  }
}

export class ConflictError extends AppError {
  constructor(message = "Conflict") {
    super(message, 409);
  }
}

export class ValidationError extends AppError {
  constructor(message = "Validation Error") {
    super(message, 400);
  }
}

export class CycleDetectedError extends AppError {
  constructor(message = "Cycle detected in graph") {
    super(message, 422);
  }
}
