export type IcuArguments = {
  projectCount: { count: number };
  storyCount: { count: number };
  sprintCount: { count: number };
  eventCount: { count: number };
  dueDate: { date: Date | number };
  emptyTaskCount: { count: number };
};

type CompiledMessages = {
  [K in keyof IcuArguments]: (args: IcuArguments[K]) => string;
};

declare const messages: { EN: CompiledMessages; SV: CompiledMessages };
export default messages;
