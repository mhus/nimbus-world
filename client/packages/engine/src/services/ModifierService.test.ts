/**
 * ModifierService tests
 */

import { ModifierService, ModifierStack, Modifier } from './ModifierService';

describe('ModifierService', () => {
  let modifierService: ModifierService;

  beforeEach(() => {
    modifierService = new ModifierService();
  });

  afterEach(() => {
    modifierService.dispose();
  });

  describe('Basic Operations', () => {
    it('should create a modifier stack with default value', () => {
      const mockAction = jest.fn();
      const stack = modifierService.createModifierStack('test', 42, mockAction);

      expect(stack).toBeDefined();
      expect(stack.stackName).toBe('test');
      expect(stack.currentValue).toBe(42);
      expect(mockAction).toHaveBeenCalledWith(42);
    });

    it('should not allow duplicate stack names', () => {
      modifierService.createModifierStack('test', 42, jest.fn());

      expect(() => {
        modifierService.createModifierStack('test', 10, jest.fn());
      }).toThrow("ModifierStack 'test' already exists");
    });

    it('should add modifiers to a stack', () => {
      const mockAction = jest.fn();
      modifierService.createModifierStack('test', 0, mockAction);

      const modifier = modifierService.addModifier('test', { value: 5, prio: 100 });

      expect(modifier).toBeDefined();
      expect(modifier.value).toBe(5);
      expect(modifier.prio).toBe(100);
      expect(mockAction).toHaveBeenCalledWith(5);
    });

    it('should throw when adding modifier to non-existent stack', () => {
      expect(() => {
        modifierService.addModifier('nonexistent', { value: 5, prio: 100 });
      }).toThrow("ModifierStack 'nonexistent' does not exist");
    });
  });

  describe('Priority System', () => {
    it('should prioritize lower prio values (higher priority)', () => {
      const mockAction = jest.fn();
      modifierService.createModifierStack<number>('windForce', 0, mockAction);

      // Add modifier with prio 1000
      modifierService.addModifier('windForce', { value: 2, prio: 1000 });
      expect(mockAction).toHaveBeenLastCalledWith(2);

      // Add modifier with prio 100 (higher priority, should win)
      modifierService.addModifier('windForce', { value: 10, prio: 100 });
      expect(mockAction).toHaveBeenLastCalledWith(10);
    });

    it('should use newest modifier when priorities are equal', () => {
      const mockAction = jest.fn();
      modifierService.createModifierStack<number>('test', 0, mockAction);

      modifierService.addModifier('test', { value: 1, prio: 100 });
      expect(mockAction).toHaveBeenLastCalledWith(1);

      // Same priority, newer should win
      modifierService.addModifier('test', { value: 2, prio: 100 });
      expect(mockAction).toHaveBeenLastCalledWith(2);
    });

    it('should fall back to default when all modifiers are removed', () => {
      const mockAction = jest.fn();
      modifierService.createModifierStack<number>('test', 0, mockAction);

      const modifier1 = modifierService.addModifier('test', { value: 5, prio: 100 });
      const modifier2 = modifierService.addModifier('test', { value: 10, prio: 50 });

      expect(mockAction).toHaveBeenLastCalledWith(10);

      modifier2.close();
      expect(mockAction).toHaveBeenLastCalledWith(5);

      modifier1.close();
      expect(mockAction).toHaveBeenLastCalledWith(0);
    });
  });

  describe('Modifier Updates', () => {
    it('should update action when modifier value changes', () => {
      const mockAction = jest.fn();
      modifierService.createModifierStack<number>('test', 0, mockAction);

      const modifier = modifierService.addModifier('test', { value: 10, prio: 100 });
      expect(mockAction).toHaveBeenLastCalledWith(10);

      modifier.setValue(20);
      expect(mockAction).toHaveBeenLastCalledWith(20);
    });

    it('should maintain priority when modifier value changes', () => {
      const mockAction = jest.fn();
      modifierService.createModifierStack<number>('test', 0, mockAction);

      const modifier1 = modifierService.addModifier('test', { value: 10, prio: 100 });
      const modifier2 = modifierService.addModifier('test', { value: 5, prio: 200 });

      // modifier1 has higher priority (lower prio value)
      expect(mockAction).toHaveBeenLastCalledWith(10);

      // Change modifier2 value, modifier1 should still win
      modifier2.setValue(50);
      expect(mockAction).toHaveBeenLastCalledWith(10);

      // Change modifier1 value
      modifier1.setValue(15);
      expect(mockAction).toHaveBeenLastCalledWith(15);
    });
  });

  describe('Example from Specification', () => {
    it('should work exactly as specified in the example', () => {
      const mockEnvironmentService = {
        windForce: 0,
        setWindForce(value: number) {
          this.windForce = value;
        },
      };

      // Create stack with default value 0
      modifierService.createModifierStack<number>(
        'windForce',
        0,
        (newValue: number) => {
          mockEnvironmentService.setWindForce(newValue);
        }
      );
      expect(mockEnvironmentService.windForce).toBe(0);

      // Add modifier by weather with prio 1000
      const windModifierByWeather = modifierService.addModifier<number>('windForce', {
        value: 2,
        prio: 1000,
      });
      expect(mockEnvironmentService.windForce).toBe(2);

      // Add modifier by effect with prio 100 (higher priority)
      const windModifierByEffect = modifierService.addModifier<number>('windForce', {
        value: 10,
        prio: 100,
      });
      expect(mockEnvironmentService.windForce).toBe(10);

      // Update effect modifier
      windModifierByEffect.setValue(20);
      expect(mockEnvironmentService.windForce).toBe(20);

      // Update weather modifier (effect still wins)
      windModifierByWeather.setValue(5);
      expect(mockEnvironmentService.windForce).toBe(20);

      // Update effect modifier again
      windModifierByEffect.setValue(40);
      expect(mockEnvironmentService.windForce).toBe(40);

      // Close effect modifier (weather takes over)
      windModifierByEffect.close();
      expect(mockEnvironmentService.windForce).toBe(5);
    });
  });

  describe('Type Safety', () => {
    it('should work with different types', () => {
      const boolAction = jest.fn();
      const stringAction = jest.fn();
      const objectAction = jest.fn();

      modifierService.createModifierStack<boolean>('bool', false, boolAction);
      modifierService.createModifierStack<string>('string', 'default', stringAction);
      modifierService.createModifierStack<{ x: number; y: number }>(
        'object',
        { x: 0, y: 0 },
        objectAction
      );

      modifierService.addModifier('bool', { value: true, prio: 100 });
      expect(boolAction).toHaveBeenLastCalledWith(true);

      modifierService.addModifier('string', { value: 'test', prio: 100 });
      expect(stringAction).toHaveBeenLastCalledWith('test');

      modifierService.addModifier('object', { value: { x: 10, y: 20 }, prio: 100 });
      expect(objectAction).toHaveBeenLastCalledWith({ x: 10, y: 20 });
    });
  });

  describe('Stack Management', () => {
    it('should get modifier stack', () => {
      const stack = modifierService.createModifierStack('test', 0, jest.fn());
      const retrieved = modifierService.getModifierStack('test');

      expect(retrieved).toBe(stack);
    });

    it('should return undefined for non-existent stack', () => {
      const stack = modifierService.getModifierStack('nonexistent');
      expect(stack).toBeUndefined();
    });

    it('should check if stack exists', () => {
      modifierService.createModifierStack('test', 0, jest.fn());

      expect(modifierService.hasStack('test')).toBe(true);
      expect(modifierService.hasStack('nonexistent')).toBe(false);
    });

    it('should remove stack', () => {
      modifierService.createModifierStack('test', 0, jest.fn());
      expect(modifierService.hasStack('test')).toBe(true);

      modifierService.removeStack('test');
      expect(modifierService.hasStack('test')).toBe(false);
    });

    it('should get all stack names', () => {
      modifierService.createModifierStack('stack1', 0, jest.fn());
      modifierService.createModifierStack('stack2', 0, jest.fn());

      const names = modifierService.stackNames;
      expect(names).toContain('stack1');
      expect(names).toContain('stack2');
      expect(names.length).toBe(2);
    });
  });

  describe('Force Update', () => {
    it('should execute action when force update is called', () => {
      const mockAction = jest.fn();
      const stack = modifierService.createModifierStack('test', 42, mockAction);

      // Clear previous calls
      mockAction.mockClear();

      // Force update without value change
      stack.update(true);
      expect(mockAction).toHaveBeenCalledWith(42);
    });
  });
});
