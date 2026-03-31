package cc.sighs.more_maid_interaction.dsl.runtime;

import cc.sighs.more_maid_interaction.dsl.MaidscriptParser;
import cc.sighs.more_maid_interaction.dsl.TokenType;
import cc.sighs.more_maid_interaction.dsl.ast.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MaidscriptRuntime {
    private final Program program;
    private final Map<String, FunctionDecl> functions;

    public MaidscriptRuntime(Program program) {
        this.program = Objects.requireNonNull(program);
        this.functions = new LinkedHashMap<>();
        for (Declaration decl : program.declarations()) {
            if (decl instanceof FunctionDecl fn) {
                functions.put(fn.name(), fn);
            }
        }
    }

    public static MaidscriptRuntime fromSource(String source) {
        return new MaidscriptRuntime(MaidscriptParser.parse(source));
    }

    public Program program() {
        return program;
    }

    public ExecutionResult execute(String eventId, RuntimeInput input) {
        RuntimeInput safeInput = input == null ? RuntimeInput.empty(eventId) : input;
        ExecutionResult result = new ExecutionResult();
        ExecutionContext ctx = new ExecutionContext(safeInput, result);

        for (Declaration decl : program.declarations()) {
            if (decl instanceof EventDecl eventDecl && Objects.equals(eventDecl.eventId(), eventId)) {
                try {
                    ctx.executeBlock(eventDecl.body(), true);
                } catch (StopSignal stopSignal) {
                    result.setStopped(true);
                    break;
                }
            }
        }

        return result;
    }

    @FunctionalInterface
    private interface Callable {
        Object call(List<Object> args);
    }

    private final class ExecutionContext {
        private final RuntimeInput input;
        private final ExecutionResult result;
        private final Deque<Map<String, Object>> scopes = new ArrayDeque<>();
        private int steps;

        private ExecutionContext(RuntimeInput input, ExecutionResult result) {
            this.input = input;
            this.result = result;
            this.steps = 0;
            Map<String, Object> globals = new HashMap<>();
            installGlobals(globals);
            scopes.push(globals);
        }

        private void installGlobals(Map<String, Object> globals) {
            Map<String, Object> event = new HashMap<>();
            event.put("id", input.event().id());
            event.put("intensity", input.event().intensity());
            event.put("payload", new HashMap<>(input.event().payload()));

            Map<String, Object> mood = new HashMap<>();
            mood.put("top", input.moodTop());
            mood.put("prob", (Callable) args -> {
                requireArity("mood.prob", args, 1);
                String name = asString(args.get(0));
                return input.moodDistribution().getOrDefault(name, 0.0);
            });

            Map<String, Object> memory = new HashMap<>();
            memory.put("count", (Callable) args -> {
                requireArity("memory.count", args, 2);
                return input.services().memoryCount(asString(args.get(0)), asInt(args.get(1)));
            });
            memory.put("frequency", (Callable) args -> {
                requireArity("memory.frequency", args, 1);
                return input.services().memoryFrequency(asString(args.get(0)));
            });
            memory.put("has_recent", (Callable) args -> {
                requireArity("memory.has_recent", args, 2);
                return input.services().memoryHasRecent(asString(args.get(0)), asInt(args.get(1)));
            });
            memory.put("add", (Callable) args -> {
                requireArity("memory.add", args, 1);
                result.addMemoryTag(asString(args.get(0)));
                return null;
            });

            Map<String, Object> story = new HashMap<>();
            story.put("has_flag", (Callable) args -> {
                requireArity("story.has_flag", args, 1);
                return input.services().storyHasFlag(asString(args.get(0)));
            });
            story.put("set_flag", (Callable) args -> {
                requireArity("story.set_flag", args, 1);
                result.addStoryFlag(asString(args.get(0)));
                return null;
            });

            globals.put("state", new HashMap<>(input.state()));
            globals.put("emotion", new HashMap<>(input.emotion()));
            globals.put("social", new HashMap<>(input.social()));
            globals.put("context", new HashMap<>(input.context()));
            globals.put("event", event);
            globals.put("mood", mood);
            globals.put("memory", memory);
            globals.put("story", story);

            globals.put("clamp", (Callable) args -> {
                requireArity("clamp", args, 3);
                return clamp(asNumber(args.get(0)), asNumber(args.get(1)), asNumber(args.get(2)));
            });
            globals.put("lerp", (Callable) args -> {
                requireArity("lerp", args, 3);
                double a = asNumber(args.get(0));
                double b = asNumber(args.get(1));
                double t = asNumber(args.get(2));
                return a + (b - a) * t;
            });
            globals.put("smoothstep", (Callable) args -> {
                requireArity("smoothstep", args, 1);
                double x = clamp(asNumber(args.get(0)), 0, 1);
                return x * x * (3 - 2 * x);
            });
            globals.put("rand", (Callable) args -> {
                requireArity("rand", args, 0);
                return input.services().random();
            });
            globals.put("rand_range", (Callable) args -> {
                requireArity("rand_range", args, 2);
                double min = asNumber(args.get(0));
                double max = asNumber(args.get(1));
                return min + (max - min) * input.services().random();
            });
            globals.put("contains", (Callable) args -> {
                requireArity("contains", args, 2);
                return asString(args.get(0)).contains(asString(args.get(1)));
            });
            globals.put("cooldown_ready", (Callable) args -> {
                requireArity("cooldown_ready", args, 2);
                return input.services().cooldownReady(asString(args.get(0)), asInt(args.get(1)));
            });

            globals.put("apply_delta", (Callable) args -> {
                requireArity("apply_delta", args, 1);
                Map<String, Object> map = asMap(args.get(0));
                for (Map.Entry<String, Object> e : map.entrySet()) {
                    result.addDelta(e.getKey(), asNumber(e.getValue()));
                }
                return null;
            });
            globals.put("mood_bias", (Callable) args -> {
                requireArity("mood_bias", args, 1);
                Map<String, Object> map = asMap(args.get(0));
                for (Map.Entry<String, Object> e : map.entrySet()) {
                    result.addMoodBias(e.getKey(), asNumber(e.getValue()));
                }
                return null;
            });
            globals.put("say", (Callable) args -> {
                requireArity("say", args, 1);
                result.addAction("say", Map.of("text", asString(args.get(0))));
                return null;
            });
            globals.put("bubble", (Callable) args -> {
                requireArity("bubble", args, 1);
                result.addAction("bubble", Map.of("text", asString(args.get(0))));
                return null;
            });
            globals.put("sound_hint", (Callable) args -> {
                if (args.isEmpty()) {
                    throw new ScriptRuntimeException("sound_hint requires at least 1 argument.");
                }
                String id = asString(args.get(0));
                double volume = args.size() >= 2 ? asNumber(args.get(1)) : 1.0;
                double pitch = args.size() >= 3 ? asNumber(args.get(2)) : 1.0;
                result.addAction("sound_hint", Map.of("id", id, "volume", volume, "pitch", pitch));
                return null;
            });
            globals.put("animation_hint", (Callable) args -> {
                requireArity("animation_hint", args, 1);
                result.addAction("animation_hint", Map.of("id", asString(args.get(0))));
                return null;
            });

            for (FunctionDecl fn : functions.values()) {
                globals.put(fn.name(), (Callable) args -> invokeFunction(fn, args));
            }
        }

        private Object invokeFunction(FunctionDecl fn, List<Object> args) {
            Map<String, Object> fnScope = new HashMap<>();
            List<String> params = fn.params();
            for (int i = 0; i < params.size(); i++) {
                fnScope.put(params.get(i), i < args.size() ? args.get(i) : null);
            }

            scopes.push(fnScope);
            try {
                executeBlock(fn.body(), false);
            } catch (ReturnSignal returnSignal) {
                return returnSignal.value;
            } finally {
                scopes.pop();
            }
            return null;
        }

        private void executeBlock(BlockStmt block, boolean pushScope) {
            if (pushScope) {
                scopes.push(new HashMap<>());
            }
            try {
                for (Stmt stmt : block.statements()) {
                    executeStmt(stmt);
                }
            } finally {
                if (pushScope) {
                    scopes.pop();
                }
            }
        }

        private void executeStmt(Stmt stmt) {
            step();
            if (stmt instanceof LetStmt letStmt) {
                Object value = eval(letStmt.initializer());
                scopes.peek().put(letStmt.name(), value);
                return;
            }
            if (stmt instanceof IfStmt ifStmt) {
                if (isTruthy(eval(ifStmt.condition()))) {
                    executeBlock(ifStmt.thenBranch(), true);
                } else if (ifStmt.elseBranch() != null) {
                    executeStmt(ifStmt.elseBranch());
                }
                return;
            }
            if (stmt instanceof ReturnStmt returnStmt) {
                Object value = returnStmt.value() == null ? null : eval(returnStmt.value());
                throw new ReturnSignal(value);
            }
            if (stmt instanceof StopStmt) {
                throw new StopSignal();
            }
            if (stmt instanceof ExprStmt exprStmt) {
                eval(exprStmt.expression());
                return;
            }
            if (stmt instanceof BlockStmt blockStmt) {
                executeBlock(blockStmt, true);
                return;
            }
            throw new ScriptRuntimeException("Unsupported statement type: " + stmt.getClass().getSimpleName());
        }

        private Object eval(Expr expr) {
            step();
            if (expr instanceof LiteralExpr literalExpr) {
                return literalExpr.value();
            }
            if (expr instanceof IdentifierExpr identifierExpr) {
                return lookup(identifierExpr.name());
            }
            if (expr instanceof UnaryExpr unaryExpr) {
                Object right = eval(unaryExpr.right());
                if (unaryExpr.operator() == TokenType.BANG) {
                    return !isTruthy(right);
                }
                if (unaryExpr.operator() == TokenType.MINUS) {
                    return -asNumber(right);
                }
                throw new ScriptRuntimeException("Unsupported unary operator: " + unaryExpr.operator());
            }
            if (expr instanceof BinaryExpr binaryExpr) {
                return evalBinary(binaryExpr);
            }
            if (expr instanceof AccessExpr accessExpr) {
                Object target = eval(accessExpr.target());
                if (target instanceof Map<?, ?> map) {
                    return map.get(accessExpr.member());
                }
                throw new ScriptRuntimeException("Cannot access member on non-object value.");
            }
            if (expr instanceof MapLiteralExpr mapLiteralExpr) {
                return evalMapLiteral(mapLiteralExpr);
            }
            if (expr instanceof CallExpr callExpr) {
                Object callee = eval(callExpr.callee());
                if (!(callee instanceof Callable callable)) {
                    throw new ScriptRuntimeException("Callee is not callable.");
                }
                List<Object> args = new ArrayList<>();
                for (Expr argExpr : callExpr.args()) {
                    args.add(eval(argExpr));
                }
                if (callExpr.blockArg() != null) {
                    args.add(evalMapLiteral(callExpr.blockArg()));
                }
                return callable.call(args);
            }
            throw new ScriptRuntimeException("Unsupported expression type: " + expr.getClass().getSimpleName());
        }

        private Object evalBinary(BinaryExpr expr) {
            if (expr.operator() == TokenType.AND_AND) {
                Object left = eval(expr.left());
                if (!isTruthy(left)) return false;
                return isTruthy(eval(expr.right()));
            }
            if (expr.operator() == TokenType.OR_OR) {
                Object left = eval(expr.left());
                if (isTruthy(left)) return true;
                return isTruthy(eval(expr.right()));
            }

            Object left = eval(expr.left());
            Object right = eval(expr.right());
            return switch (expr.operator()) {
                case PLUS -> {
                    if (left instanceof String || right instanceof String) {
                        yield asString(left) + asString(right);
                    }
                    yield asNumber(left) + asNumber(right);
                }
                case MINUS -> asNumber(left) - asNumber(right);
                case STAR -> asNumber(left) * asNumber(right);
                case SLASH -> asNumber(left) / asNumber(right);
                case PERCENT -> asNumber(left) % asNumber(right);
                case GREATER -> asNumber(left) > asNumber(right);
                case GREATER_EQUAL -> asNumber(left) >= asNumber(right);
                case LESS -> asNumber(left) < asNumber(right);
                case LESS_EQUAL -> asNumber(left) <= asNumber(right);
                case EQUAL_EQUAL -> isEqual(left, right);
                case BANG_EQUAL -> !isEqual(left, right);
                default -> throw new ScriptRuntimeException("Unsupported binary operator: " + expr.operator());
            };
        }

        private Map<String, Object> evalMapLiteral(MapLiteralExpr mapExpr) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (MapEntry entry : mapExpr.entries()) {
                map.put(entry.key(), eval(entry.value()));
            }
            return map;
        }

        private Object lookup(String name) {
            for (Map<String, Object> scope : scopes) {
                if (scope.containsKey(name)) {
                    return scope.get(name);
                }
            }
            throw new ScriptRuntimeException("Undefined identifier: " + name);
        }

        private void step() {
            steps++;
            if (steps > input.maxSteps()) {
                throw new ScriptRuntimeException("Script step limit exceeded: " + input.maxSteps());
            }
        }
    }

    private static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value instanceof String s) return !s.isEmpty();
        if (value instanceof Map<?, ?> m) return !m.isEmpty();
        if (value instanceof List<?> l) return !l.isEmpty();
        return true;
    }

    private static boolean isEqual(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number an && b instanceof Number bn) {
            return Double.compare(an.doubleValue(), bn.doubleValue()) == 0;
        }
        return Objects.equals(a, b);
    }

    private static double asNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new ScriptRuntimeException("Expected number, got: " + value);
    }

    private static int asInt(Object value) {
        return (int) Math.round(asNumber(value));
    }

    private static String asString(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        throw new ScriptRuntimeException("Expected map object, got: " + value);
    }

    private static void requireArity(String fn, List<Object> args, int expect) {
        if (args.size() != expect) {
            throw new ScriptRuntimeException(fn + " expects " + expect + " args, got " + args.size());
        }
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    private static final class ReturnSignal extends RuntimeException {
        private final Object value;

        private ReturnSignal(Object value) {
            this.value = value;
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    private static final class StopSignal extends RuntimeException {
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
